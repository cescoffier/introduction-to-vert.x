# Accessing data - the reactive way

This post is the fourth post of the _introduction to Vert.x_  series. In this post we are going to see how we can use JDBC in an Eclipse Vert.x application, and this, using the asynchronous API provided by the [vertx-jdbc-client](http://vertx.io/docs/vertx-jdbc-client/java/). But before diving into JDBC and other SQL subtleties, we are going to talk about `Futures`.

## Previously in the introduction to vert.x series

Let’s start by refreshing our mind about the previous posts:

    1. The first post has described how to build a vert.x application with Maven and execute unit tests.
    2. The second post has described how this application became configurable.
    3. The third post has introduced vertx-web, and a collection management application has been developed. 
    This application exposes a REST API used by a HTML/JavaScript frontend.

In this post, let's fix the major flaw of our application: the in-memory back-end. The current application uses an in-memory `Map` to store the products (articles), so very useful as we loose the content every time we restart the application. Let's use a database. In this post we are going to use PostgreSQL, but you can use any database providing a JDBC driver. For instance, our tests are going to use HSQL. Interactions with the database are asynchronous and made using the `vertx-jdbc-client`. But before diving into these JDBC and SQL details, let's introduce the Vert.x `Future` class and explain how it's going to make asynchronous coordination much simpler.

The code of this post is available on the [Github repo](https://github.com/cescoffier/introduction-to-vert.x), in the `post-4` directory.

## Asynchronous API

One of the Eclipse Vert.x characteristics is its asynchronous and non-blocking nature. With an asynchronous API, you don’t wait for a result,  but you are notified when this result is ready, the operation has completed.... Just to illustrate this, let’s take a very simple example.

Let’s imagine an `retrieve` method. Traditionally, you would use it like this: `String r = retrieve()`. This is a synchronous API as the execution continue when for the result has been returned by the `retrieve` method. An asynchronous version of this API would be: `retrieve(r -> { /* do something with the result */ })`. In this version, you pass a function (`Handler` in the Vert.x lingo) called when the result has been computed.  This function does not return anything, and is called when the result has been computed. For instance, the `retrieve` method code could be something like:

```java
public void retrieve(Handler<String> resultHandler) {
    fileSystem.read(fileName, res -> {
        resultHandler.handle(res);
    });
}
```

Just to avoid misconceptions, asynchronous APIs are not about threads. As we can see in the `retrieve` example, there are no threads involved, and most Vert.x applications are using a very small number of threads while being asynchronous and non-blocking. Also, it's important to notice that the method is non-blocking. The `retrieve` method may return before the `resultHandler` is called.

Asynchronous operations can also ... fail. So, we need a way to encapsulate these failures and forward them to the callback. We can't use `try-catch` blocks because of the asynchrony. To capture the result or the failure or an operation, Vert.x proposes the `AsyncResult` type. Our `Handler` does not receive the plain result anymore but an `AsyncResult` encapsulating the result in case of success or the error if something bad happens:

```java
public void retrieve(Handler<AsyncResult<String>> resultHandler) {
    vertx.fileSystem().readFile("fileName", ar -> {
      if (ar.failed()) {
          resultHandler.handle(Future.succeededFuture(ar.result().toString()));
      } else {
        resultHandler.handle(Future.failedFuture(ar.cause()));
      }
    });
}
```

Look at the `if-else` block. You will see it a lot when using `AsyncResult`. I won't detail `Future` here, it's covered a bit later, just be patient. For now, `Future.succeededFuture` and `Future.failedFuture` are just factory methods creating `AsyncResult` instances. On the consumer side, you would do:

```java
retrieve(ar -> {
  if (ar.failed()) {
    // Handle the failure, the exception is retrieved using ar.cause()
    Throwable cause = ar.cause();
    // ...
   } else {
    // Made it, the result is in ar.result()
    String content = ar.result();
    // ...
   }
});
```

So, to summarize, an asynchronous method is a method forwarding its result or failure as a notification, generally calling a callback expecting the result.

## The asynchronous coordination dilemma

Once you have a set of asynchronous methods, you generally want to orchestrate them:

1. sequentially, so calling once another one has completed,
1. concurrently, so calling several actions at the same time and be notified when all / one of them have / has completed.

For the first case, we would do something like:

```java
retrieve(ar -> {
  if (ar.failed()) {
    // do something to recover
   } else {
    String r = ar.result();
    // call another async method
    anotherAsyncMethod(r, ar2 -> {
      if (ar2.failed()) {
        //...
      } else {
        // ...
      }
    })
   }
});
```

You can quickly spot the issue... things start getting messy. Nested callbacks reduce code readability, and this was just with 2. Image when dealing with more like we will later in this post.

For the second type of composition, you can also imagine the difficulty. In each result handler, you need to check whether or not the others have completed or failed and react. It leads to convoluted code.

## Future and CompositeFuture - async coordination made easy

To reduce the code complexity, Vert.x proposes a class named `Future`. A `Future` is an object that encapsulates a result of an action that may, or may not, have occurred yet. Unlike regular Java Future, Vert.x `Future` are non-blocking and a `Handler` is called when the `Future` is completed or `failed`. The `Future` class implements `AsyncResult` as it represents a result computed asynchronously.

__A NOTE ABOUT JAVA FUTURE:__ Regular Java `Future` are blocking. Calling `get` blocks the caller thread until the result is received (or a timeout is reached). Vert.x `Future`s also have a `get` method returning `null` if the result is not yet received. They also expect a handler to be attached to them, calling it when the result is received.

Creating a `Future` object is done using the `Future.future()` factory method:

```java
Future<Integer> future = Future.future();
future.complete(1); // Completes the Future with a result
future.fail(exception); // Fails the Future

// To be notified when the future has been completed or failed
future.setHandler(ar -> {
  // Handler called with the result or the failure, 
  // ar is an AsyncResult
});
```

Let's revisit our `retrieve` method. Instead of taking a callback as parameter, we can return a `Future` object:

```java
public Future<String> retrieve() {
    Future<String> future = Future.future();
    vertx.fileSystem().readFile("fileName", ar -> {
        if (ar.failed()) {
            future.failed(ar.cause());
        } else {
            future.complete(ar.result().toString());
        }
    });
    return future;
}
```

As mentioned above, it's important to understand that this `retrieve` method returns its `Future` probably before it receives a value. So, the `return future;` statement is executed before it executes `future.handle(...)`. Vert.x seasoned developers would have written this code a bit differently:

```java
public Future<String> retrieve() {
    Future<String> future = Future.future();
    vertx.fileSystem().readFile("fileName", ar -> future.handle(ar.map(Buffer::toString)));
    return future;
}
```

We are going to cover this API in a few minutes. but first, let's look at the caller side, things do not change much. The handler is attached on the returned `Future`.

```java
retrieve().setHandler(ar -> {
  if (ar.failed()) {
    // Handle the failure, the exception is retrieved using ar.cause()
    Throwable cause = ar.cause();
    // ...
   } else {
    // Made it, the result is in ar.result()
    int r = ar.result();
    // ...
   }
});
```

Where things become much easier is when you need to compose asynchronous action. Sequential composition is handled using the `compose` method:

```java
retrieve()
  .compose(this::anotherAsyncMethod)
  .setHandler(ar -> {
    // ar.result is the final result
    // if any stage fails, ar.cause is the thrown exception
  });
```

`Future.compose` takes as parameter a function consuming the result of the previous `Future` and returning another `Future`. This way you can chain many asynchronous actions.

What about concurrent composition. Let's imagine you want to invoke 2 unrelated operation and be notified when both have completed:

```java
Future<String> future1 = retrieve();
Future<Integer> future2 = anotherAsyncMethod();
CompositeFuture.all(future1, future2).setHandler(ar -> {
  // called when either all future have completed successfully (success), 
  // or one failed (failure)
});
```

`CompositeFuture` is a companion class simplifying drastically concurrent composition. `all` is not the only operator provided, you can use `join`, `any`...

Using `Future` and `CompositeFuture` make the code much more readable and maintainable. Vert.x also supports RX Java to manage asynchronous composition, this will be covered in another post.

## JDBC yes, but asynchronous

So, now that we have seen some basics about asynchronous APIs and `Future`s, let’s have a look to the `vertx-jdbc-client`. This Vert.x module lets us interact with a database through a JDBC driver. These interactions are asynchronous, so when you were doing:

```java
String sql = "SELECT * FROM Products";
ResultSet rs = stmt.executeQuery(sql);
```

When you use the `vertx-jdbc-client`, it becomes:

```java
connection.query("SELECT * FROM Products", result -> {
        // do something with the result
});
```

This model avoids waiting for the result. You are notified when the result has been retrieved from the database.

__A Note on JDBC__: JDBC is a blocking API by default. To interact with the database, Vert.x delegates to a _worker_  thread. While it's asynchronous, it's not totally non-blocking. However, the Vert.x ecosystem also provides a truly non-blocking clients for MySQL and PostgreSQL.

Let’s now modify our application to use a database to store our products (articles).

## Some Maven dependencies

The first things we need to do it to declare two new Maven dependencies in our `pom.xml` file:

```xml
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-jdbc-client</artifactId>
  <version>${vertx.version}</version>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <version>9.4.1212</version>
</dependency>
```

The first dependency provides the `vertx-jdbc-client`, while the second one provides the PostgreSQL JDBC driver. If you  want to use another database, change this dependency. You will also need to change the JDBC url and JDBC driver class name in the code.

## Initializing the JDBC client

Now that we have added these dependencies, it’s time to create our JDBC client. But it needs to be configured. Edit the `src/main/conf/my-application-conf.json` to match the following content:

```json
{
  "HTTP_PORT": 8082,

  "url": "jdbc:postgresql://localhost:5432/my_read_list",
  "driver_class": "org.postgresql.Driver",
  "user": "user",
  "password": "password"
}
```

We add the `url`, `driver_class`, `user` and `password` entries. Notice that if you use a different database, the configuration will likely be different.

Now that the configuration is written, we need to create an instance of JDBC client. In the `MyFirstVerticle` class, declare a new field `JDBCClient jdbc;`, and update the end of the `start` method to become:

```java
ConfigRetriever retriever = ConfigRetriever.create(vertx);
retriever.getConfig(
    config -> {
        if (config.failed()) {
            fut.fail(config.cause());
        } else {
            // Create the JDBC client
            jdbc = JDBCClient.createShared(vertx, config.result(), 
                "My-Reading-List");
            vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(
                    // Retrieve the port from the configuration,
                    // default to 8080.
                    config.result().getInteger("HTTP_PORT", 8080),
                    result -> {
                        if (result.succeeded()) {
                            fut.complete();
                        } else {
                            fut.fail(result.cause());
                        }
                    }
                );
        }
    }
);
```

Ok, we have the _client_ configured with our configuration, we need a connection to the database. This is achieved using the _jdbc.getConnection_ method that provides its result (the connection) to a `Handler<AsyncResult<SQLConnection>>`. This handler is notified when the connection with the database is established or if something bad happens during the process. While we could use the method directly, let's extract the retrieval of a connection to a separate method and returns a `Future`:

```java
private Future<SQLConnection> connect() {
    Future<SQLConnection> future = Future.future();                                  // 1
    jdbc.getConnection(ar ->                                                         // 2
        future.handle(ar.map(connection ->                                           // 3
                connection.setOptions(new SQLOptions().setAutoGeneratedKeys(true))   // 4
            )
        )
    );
    return future;                                                                   // 5
}
```

Let's have a deeper look to this method. First we create a `Future` object (1) that we return at the end of the method (5). This `Future` will be completed or failed depending wether or not we successfully retrieve a connection to the database. This is done in (2). The function we passed to `getConnection` receives an `AsyncResult`. `Future` have a method (`handle`) to directly completes or fails based on an `AsyncResult`. To `handle` is equivalent to:

```java
if (ar.failed()) {
  future.failed(ar.cause());
} else {
  future.complete(ar.result());
}
```

just... shorter.

However, before passing the `AsyncResult` to `future`, we want to configure the connection to enable the key generation. For this we use the `AsyncResult.map` method. This method creates another instance of `AsyncResult` based on the given one and apply a mapper function on the result. If the given one encapsulate a failure, the created one encapsulate the same failure. If the input is a success, the mapper function is applied on the result.

## We need articles

Now that we have a JDBC client, and a way to retrieve a connection to the database, it's time to insert articles. But because we use a relational database, we first need to create the table. Create the following method:

```java
private Future<SQLConnection> createTableIfNeeded(SQLConnection connection) {
    Future<SQLConnection> future = Future.future();
    vertx.fileSystem().readFile("tables.sql", ar -> {
        if (ar.failed()) {
            future.fail(ar.cause());
        } else {
            connection.execute(ar.result().toString(),
                ar2 -> future.handle(ar2.map(connection))
            );
        }
    });
    return future;
}
```

The method also returns a `Future`. Attentive readers would spot that this is typically a method we can use in a `Future.compose` construct. This method body is quite simple. As usual we create a `Future` and returns it at the end of the body. Then, we read the content of the `tables.sql` file and execute the unique statement contained in this file. The `execute` method takes the SQL statement as parameter, and invokes the given function with the result. In the handler, we complete or fail the future using the `handle` method. In this case, we want to complete the future with the database connection.

So, we need the `tables.sql` file. Creates the `src/main/resources/tables.sql` file with the following content:

```sql
CREATE TABLE IF NOT EXISTS Articles (id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    url VARCHAR(200) NOT NULL)
```

Ok, so now we have a connection to the database, and the table. Let's insert articles, but only if the database is empty. For this, create the `createSomeDataIfNone` and `insert` methods:

```java
private Future<SQLConnection> createSomeDataIfNone(SQLConnection connection) {
    Future<SQLConnection> future = Future.future();
    connection.query("SELECT * FROM Articles", select -> {
        if (select.failed()) {
            future.fail(select.cause());
        } else {
            if (select.result().getResults().isEmpty()) {
                Article article1 = new Article("Fallacies of distributed computing",
                    "https://en.wikipedia.org/wiki/Fallacies_of_distributed_computing");
                Article article2 = new Article("Reactive Manifesto",
                    "https://www.reactivemanifesto.org/");
                Future<Article> insertion1 = insert(connection, article1, false);
                Future<Article> insertion2 = insert(connection, article2, false);
                CompositeFuture.all(insertion1, insertion2)
                    .setHandler(r -> future.handle(r.map(connection)));
            } else {
                // Boring... nothing to do.
                future.complete(connection);
            }
        }
    });
    return future;
}

private Future<Article> insert(SQLConnection connection, Article article,
    boolean closeConnection) {
    Future<Article> future = Future.future();
    String sql = "INSERT INTO Articles (title, url) VALUES (?, ?)";
    connection.updateWithParams(sql,
        new JsonArray().add(article.getTitle()).add(article.getUrl()),
        ar -> {
            if (closeConnection) {
                connection.close();
            }
            future.handle(
                ar.map(res -> new Article(res.getKeys().getLong(0), 
                    article.getTitle(), article.getUrl()))
            );
        }
    );
    return future;
}
```

Let's start by the end and the `insert` method. It follows the same pattern, and uses the `updateWithParams` method to insert an article into the database. The SQL statement contains parameters injected using a JSON Array. Notice that the order of the parameter matters.  When the insertion is done (in the handler), we close the connection if requested (`closeConnection` parameter) - this is because we are going to reuse method later. Finally, we complete or fail the `future` with, on success, a new `Article` containing the generated id. So, if the insertion failed, we just forward the failure to the future. If the insertion succeed, we map it to an `Article` and complete the future with this value.

Ok, let's switch to the `createSomeDataIfNone` method. Again same pattern. But here we need a bit of coordination. Indeed, we have need to check whether the database is empty first, and if so insert two articles. To check if the database is empty, we use `connection.query` retrieving all the articles. If the result is not empty, we create two articles that we insert using the `insert` method. To execute these two insertions, we use the `CompositeFuture` construct. So both actions are executed in concurrently, and when both are done (or one fails) the handler is called. Notice that the connection is not closed.

## Putting these pieces together

It's time to assemble these pieces and see how it works. The `start` method needs to be updated to execute the following action:

1. Retrieve the configuration (already done)
1. When the configuration is retrieve, create the JDBC client (already done)
1. Retrieve a connection to the database
1. With this connection, create the table if they do not exist
1. With the same connection, check whether the database contains article, if not, insert some data
1. Close the connection
1. Start the HTTP server as we are ready to _serve_
1. Report the success of failure of the boot process to `fut`

Wow... that's a lot of actions. Fortunately, we have implemented almost all the required method in a way we can use `Future` composition. In the `start` method, replace the end of the code with:

```java
// Start sequence:
// 1 - Retrieve the configuration
//      |- 2 - Create the JDBC client
//      |- 3 - Connect to the database (retrieve a connection)
//              |- 4 - Create table if needed
//                   |- 5 - Add some data if needed
//                          |- 6 - Close connection when done
//              |- 7 - Start HTTP server
//      |- 8 - we are done!
ConfigRetriever.getConfigAsFuture(retriever)
    .compose(config -> {
        jdbc = JDBCClient.createShared(vertx, config, "My-Reading-List");

        return connect()
            .compose(connection -> {
                Future<Void> future = Future.future();
                createTableIfNeeded(connection)
                    .compose(this::createSomeDataIfNone)
                    .setHandler(x -> {
                        connection.close();
                        future.handle(x.mapEmpty());
                    });
                return future;
            })
            .compose(v -> createHttpServer(config, router));

    })
    .setHandler(fut);
```

Don't worry about the `createHttpServer` method. We will cover it shortly. The code starts by retrieving the configuration and creates the `JDBCClient`. Then, we retrieve a database connection and initialize our database. Notice that the connection is close in all cases (even failures). When the database is set up, we start the HTTP server. Finally, when everything is done, we report the result (success or failure) to the `fut` telling to Vert.x whether or not we are ready to work.

__NOTE ABOUT CLOSING CONNECTIONS__: Don’t forget to close the SQL connection when you are done. The connection will be given back to the connection pool and be recycled.

The `createHTTPServer` method is quite simple and follows the same pattern:

```java
private Future<Void> createHttpServer(JsonObject config, Router router) {
    Future<Void> future = Future.future();
    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(
            config.getInteger("HTTP_PORT", 8080),
            res -> future.handle(res.mapEmpty())
        );
    return future;
}
```

Notice the `mapEmpty`. The method returns a `Future<Void>`, as we don't care of the HTTP Server. To create an `AsyncResult<Void>` from an `AsyncResult<Something>` use the `mapEmpty` method, discarding the encapsulated result.

## Implementing the REST API on top of JDBC

So, at this point we have everything setup, but our API is still relying on our in-memory back-end. It's time to re-implement our REST API on top of JDBC. But first we need some utility methods focusing on the interaction with the database. These methods have been extracted to ease the understanding.

First, let's add the `query` method:

```java
private Future<List<Article>> query(SQLConnection connection) {
    Future<List<Article>> future = Future.future();
    connection.query("SELECT * FROM articles", result -> {
            connection.close();
            future.handle(
                result.map(rs -> rs.getRows().stream().map(Article::new)
                    .collect(Collectors.toList()))
            );
        }
    );
    return future;
}
```

This method uses again the same pattern: it creates a `Future` object and returns it. The future is completed or failed when the underlying action completes or fails. Here the action is a database query. The method executes the query and upon success, for each _row_ creates a new `Article`. Also notice that we close the connection regardless the success or failure of the query. It's important to release the connection, so it can be recycled.

In the same vein, let's implement `queryOne`:

```java
private Future<Article> queryOne(SQLConnection connection, String id) {
    Future<Article> future = Future.future();
    String sql = "SELECT * FROM articles WHERE id = ?";
    connection.queryWithParams(sql, new JsonArray().add(Integer.valueOf(id)),
    result -> {
        connection.close();
        future.handle(
            result.map(rs -> {
                List<JsonObject> rows = rs.getRows();
                if (rows.size() == 0) {
                    throw new NoSuchElementException("No article with id " + id);
                } else {
                    JsonObject row = rows.get(0);
                    return new Article(row);
                }
            })
        );
    });
    return future;
}
```

This method uses `queryWithParams` to inject the article id in the query. In the result handler, there is a bit more work as we need to check if the article has been found. If not, we throw a `NoSuchElementException` that would fail the `future`. This lets us generate `404` responses.

We have done queries, we need methods to update and delete. Here they are:

```java
private Future<Void> update(SQLConnection connection, String id, Article article) {
    Future<Void> future = Future.future();
    String sql = "UPDATE articles SET title = ?, url = ? WHERE id = ?";
    connection.updateWithParams(sql, new JsonArray().add(article.getTitle())
            .add(article.getUrl())
            .add(Integer.valueOf(id)),
        ar -> {
            connection.close();
            if (ar.failed()) {
                future.fail(ar.cause());
            } else {
                UpdateResult ur = ar.result();
                if (ur.getUpdated() == 0) {
                    future.fail(new NoSuchElementException("No article with id "
                        + id));
                } else {
                    future.complete();
                }
            }
        });
    return future;
}

private Future<Void> delete(SQLConnection connection, String id) {
    Future<Void> future = Future.future();
    String sql = "DELETE FROM Articles WHERE id = ?";
    connection.updateWithParams(sql,
        new JsonArray().add(Integer.valueOf(id)),
        ar -> {
            connection.close();
            if (ar.failed()) {
                future.fail(ar.cause());
            } else {
                if (ar.result().getUpdated() == 0) {
                    future.fail(new NoSuchElementException("No article with id "
                        + id));
                } else {
                    future.complete();
                }
            }
        }
    );
    return future;
}
```

They are very similar and follows the same pattern (again!).

That's great but it does not implement our REST API. So, let's focus on this now. Just to refresh our mind, here is the methods we need to update:

* `getAll` returns all the articles.
* `addOne` inserts a new article. Article details are given in the request body.
* `deleteOne` deletes a specific article. The id is given as a _path parameter_.
* `getOne` provides the JSON representation of a specific article. The id is given as a _path parameter_.
* `updateOne` updates a specific article. The id is given as a _path parameter_. The new details are in the request body.

Because we have extract the database interactions in their own method, implementing this method is straightforward. For instance, the `getAll` method is:

```java
private void getAll(RoutingContext rc) {
    connect()
        .compose(this::query)
        .setHandler(ok(rc));
}
```

We retrieve a connection using the `connect` method. Then we compose (sequential composition) this with the `query` method, and we attach a handler. This handler is `ok(rc)` which is provided in the `ActionHelper` class. It basically provides the JSON representation or manage the error responses (`500`, `404`).

Following the same pattern, the other methods are implemented as follows:

```java
private void addOne(RoutingContext rc) {
    Article article = rc.getBodyAsJson().mapTo(Article.class);
    connect()
        .compose(connection -> insert(connection, article, true))
        .setHandler(created(rc));
}


private void deleteOne(RoutingContext rc) {
    String id = rc.pathParam("id");
    connect()
        .compose(connection -> delete(connection, id))
        .setHandler(noContent(rc));
}


private void getOne(RoutingContext rc) {
    String id = rc.pathParam("id");
    connect()
        .compose(connection -> queryOne(connection, id))
        .setHandler(ok(rc));
}

private void updateOne(RoutingContext rc) {
    String id = rc.request().getParam("id");
    Article article = rc.getBodyAsJson().mapTo(Article.class);
    connect()
        .compose(connection ->  update(connection, id, article))
        .setHandler(noContent(rc));
}
```

## Test, test, and test again

If we run the application tests right now, it fails. First we need to update the configuration to pass the JDBC url and related details, but wait... we also need a database. We don't necessarily want to use PostGreSQL in our unit test. Let's use HSQL, an in-memory database. To do that, first, add the following dependency in the `pom.xml`:

```xml
<dependency>
    <groupId>org.hsqldb</groupId>
    <artifactId>hsqldb</artifactId>
    <version>2.4.0</version>
    <scope>test</scope>
</dependency>
```

But wait, if you already use JDBC or database in general, you know that each database use a different dialects (that's the power of standards). Here, we can't use the same table creation statement because HSQL does not understand the PostGreSQL dialect. So create the `src/test/resources/tables.sql` with the following content:

```sql
CREATE TABLE IF NOT EXISTS Articles (id INTEGER IDENTITY,
    title VARCHAR(200),
    url VARCHAR(200))
```

It's the equivalent statement in the HSQL dialect. How would that work? When Vert.x reads a file it also checks the _classpath_ (and `src/test/resources` is included in the _test classpath_). When running test, this file superseds the initial file we created.

We need to slightly update our tests to configure the `JDBCClient`. In the `MyFirstVerticleTest` class, change the `DeploymentOption` object created in the `setUp` method to be:

```java
DeploymentOptions options = new DeploymentOptions()
    .setConfig(new JsonObject()
        .put("HTTP_PORT", port)
        .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
        .put("driver_class", "org.hsqldb.jdbcDriver")
);
```

In addition to the `HTTP_PORT`, we also put the JDBC url and the class of the JDBC driver.

Now, you should be able to run the test with: `mvn clean test`.

## Show time

This time we want to use a PostGreSQL instance. I'm going to use Docker, but use your favorite approach. With Docker, I start my instance as follows:

```bash
docker run --name some-postgres -e POSTGRES_USER=user \
    -e POSTGRES_PASSWORD=password \
    -e POSTGRES_DB=my_read_list \
    -p 5432:5432 -d postgres
```

Let’s now run our application:

```java
mvn compile vertx:run
```

Open your browser to http://localhost:8082/assets/index.html, and you should see the application using the database. This time the products are stored in a database persisted on the file system. So, if we stop and restart the application, the data is restored.

If you want to package the application, run `mvn clean package`. Then run the application using:

```bash
java -jar target/my-first-app-1.0-SNAPSHOT.jar \
    -conf src/main/conf/my-application-conf.json
```

## Conclusion

In the post we have covered two different topics. First we have introduce asynchronous composition and how `Future` helps to manage sequential and concurrent composition. With `Future` you follow a common pattern in your implementation, which is quite straightforward once you get it. Then, we have seen how JDBC can be used to implement our API. Because we use `Future`, using asynchronous JDBC is quite simple.

You may have been surprised by the asynchronous development model, but once you start using it, it’s hard to come back. Asynchronous and event-driven architecture represents how the world around us works. Embracing these give you superpowers.

In the next post, we will see how RX Java 2 can be used instead of Future.

Stay tuned, and happy coding !