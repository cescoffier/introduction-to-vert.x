# Some Rest with Vert.x

## Previously in this blog series

This post is part of the _Introduction to Vert.x_ series. So, let’s have a quick look about the content of the previous posts. In the first post, we developed a very simple Eclipse Vert.x application, and saw how this application can be tested, packaged and executed. In the last post, we saw how this application became configurable and how we can use a random port in test.

Well, nothing fancy... Let’s go a bit further this time and develop a CRUD-ish / REST-ish application. So an application exposing an HTML page interacting with the backend using a REST API. The level of RESTfullness of the API is not the topic of this post, I let you decide as it’s a very slippery topic.

So, in other words we are going to see:

    * Vert.x Web - a framework that let you create Web applications easily using Vert.x
    * How to expose static resources
    * How to develop a REST API

The code developed in this post is available in the `post-3` directory of the https://github.com/cescoffier/introduction-to-vert.x repository. We are going to start from the `post-2` codebase.

So, let’s start.

## Vert.x Web

As you may have notices in the previous posts, dealing with complex HTTP application using only `Vert.x Core` would be kind of cumbersome. That’s the main reason behind `Vert.x Web`. It makes the development of web applications really easy, without changing the philosophy.

To use Vert.x Web, you need to update the `pom.xml` file to add the following dependency:

```xml
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-web</artifactId>
  <version>${vertx.version}</version>
</dependency>
```

That’s the only thing you need to use Vert.x Web. Sweet, no?

Let’s now use it. Remember, in the previous post, when we requested http://localhost:8080, we reply a nice _Hello World_ message. Let’s do the same with Vert.x Web. Open the `io.vertx.intro.first.MyFirstVerticle` class and change the start method to be:

```java
@Override
public void start(Future<Void> fut) {
    // Create a router object.
    Router router = Router.router(vertx);

    // Bind "/" to our hello message - so we are still compatible.
    router.route("/").handler(routingContext -> {
        HttpServerResponse response = routingContext.response();
        response
            .putHeader("content-type", "text/html")
            .end("<h1>Hello from my first Vert.x 3 application</h1>");
    });


    ConfigRetriever retriever = ConfigRetriever.create(vertx);
    retriever.getConfig(
        config -> {
            if (config.failed()) {
                fut.fail(config.cause());
            } else {
                // Create the HTTP server and pass the "accept" method to the request handler.
                vertx
                    .createHttpServer()
                    .requestHandler(router::accept)
                    .listen(
                        // Retrieve the port from the configuration,
                        // default to 8080.
                        config().getInteger("HTTP_PORT", 8080),
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
}
```

You may be surprised by the length of this snippet (in comparison to the previous code). But as we are going to see, it will make our app on steroids, just be patient.

As you can see, we start by creating a `Router` object. The router is the cornerstone of Vert.x Web. This object is responsible for dispatching the HTTP requests to the _right_ handler. Two other concepts are very important in Vert.x Web:

   * Routes - which let you define how request are dispatched
   * Handlers - which are the actual action processing the requests and writing the result. Handlers can be chained.

If you understand these 3 concepts, you have understood everything in Vert.x Web.

Let’s focus on this code first:

```java
router.route("/").handler(routingContext -> {
  HttpServerResponse response = routingContext.response();
  response
      .putHeader("content-type", "text/html")
      .end("<h1>Hello from my first Vert.x 3 application</h1>");
});
```

It routes requests arriving on `/` to the given handler. Handlers receive a `RoutingContext` object. This handler is quite similar to the code we had before, and it’s quite normal as it manipulates the same type of object: `HttpServerResponse`.

Let’s now have a look to the rest of the code:

```java
//...
vertx
    .createHttpServer()
    .requestHandler(router::accept)
    .listen(
        config().getInteger("HTTP_PORT", 8080),
        result -> {
          if (result.succeeded()) {
            fut.complete();
          } else {
            fut.fail(result.cause());
          }
        }
    );
}
```

It’s basically the same code as before, except that we change the request handler. We pass `router::accept` to the handler. You may not be familiar with this notation. It’s a reference to a method (here the method `accept` from the `router` object). In other worlds, it instructs vert.x to call the accept method of the router when it receives a request.

Let’s try to see if this work:

```
mvn clean package
java -jar target/my-first-app-1.0-SNAPSHOT.jar
```

By opening http://localhost:8080 in your browser you should see the _Hello_ message. As we didn’t change the behavior of the application, our tests are still valid.

## Exposing static resources

Ok, so we have a first application using vert.x web. Let’s see some of the benefits. Let’s start with serving static resources, such as an `index.html` page. Before we go further, I should start with a disclaimer: “the HTML page we are going to see here is ugly like hell : I’m not a UI guy”. I should also add that there are probably plenty of better ways to implement this and a myriad of frameworks I should learn, but that’s not the point. I tried to keep things simple and just relying on JQuery and Bootstrap, so if you know a bit of JavaScript you can understand and edit the page.

Let’s create the HTML page that will be the entry point of our application. Create an `index.html` page in `src/main/resources/assets` with the content from here. As it’s just a HTML page with a bit of JavaScript, we won’t detail the file here.

Basically, the page is a simple CRUD UI to manage my not-yet-read articles. It was made in a generic way, so you can transpose it to your own _stuff_. The list of product (here _articles_) is displayed in the main table. You can create a new product, edit one or delete one. These actions are relying on a REST API (that we are going to implement) through AJAX calls. That’s all.

Once this page is created, edit the `io.vertx.blog.first.MyFirstVerticle` class and change the `start` method to be:

```
@Override
public void start(Future<Void> fut) {
    // Create a router object.
    Router router = Router.router(vertx);

    // Bind "/" to our hello message - so we are still compatible.
    router.route("/").handler(routingContext -> {
        HttpServerResponse response = routingContext.response();
        response
            .putHeader("content-type", "text/html")
            .end("<h1>Hello from my first Vert.x 3 application</h1>");
    });
    // Serve static resources from the /assets directory
    router.route("/assets/*").handler(StaticHandler.create("assets"));

    ConfigRetriever retriever = ConfigRetriever.create(vertx);
    retriever.getConfig(
        config -> {
            if (config.failed()) {
                fut.fail(config.cause());
            } else {
                // Create the HTTP server and pass the "accept" method to the request handler.
                vertx
                    .createHttpServer()
                    .requestHandler(router::accept)
                    .listen(
                        // Retrieve the port from the configuration,
                        // default to 8080.
                        config().getInteger("HTTP_PORT", 8080),
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
}
```

The only difference with the previous code is the `router.route("/assets/*").handler(StaticHandler.create("assets"));` line. So, what does this line mean? It’s actually quite simple. It routes requests on `/assets/*` to resources stored in the `assets` directory. So our `index.html` page is going to be served using http://localhost:8080/assets/index.html.

So, I’m sure you are impatient to see our beautiful HTML page. Let’s build and run the application:

```
mvn clean package
java -jar target/my-first-app-1.0-SNAPSHOT.jar
```

Now, open your browser to http://localhost:8080/assets/index.html. Here it is... Ugly right? I told you.

As you may notice too... the table is empty, this is because we didn’t implement the REST API yet. Let’s do that now.

## REST API with Vert.x Web

Vert.x Web makes the implementation of REST API really easy, as it basically routes your URL to the right handler. The API is very simple, and will be structured as follows:

* `GET /api/articles` => get all articles (`getAll`)
* `GET /api/articles/:id` => get the article with the corresponding id (`getOne`)
* `POST /api/articles` => add a new article (`addOne`)
* `PUT /api/articles/:id` => update an article (`updateOne`)
* `DELETE /api/articles/id` => delete an article (`deleteOne`)

### We need some data...

But before going further, let’s create our _data object_, the class representing an `Article`. Create the `src/main/java/io/vertx/intro/first/Article.java` with the following content:

```java
package io.vertx.intro.first;


import java.util.concurrent.atomic.AtomicInteger;

public class Article {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final int id;

    private String title;

    private String url;

    public Article(String title, String url) {
        this.id = COUNTER.getAndIncrement();
        this.title = title;
        this.url = url;
    }

    public Article() {
        this.id = COUNTER.getAndIncrement();
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Article setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public Article setUrl(String url) {
        this.url = url;
        return this;
    }
}
```

It’s a very simple _bean_ class (so with getters and setters). We choose this format because Vert.x is relying on Jackson to map object to and from JSON.

Now, let’s create a couple of article. In the `MyFirstVerticle` class, add the following code:

```java
// Store our products
private Map<Integer, Article> products = new LinkedHashMap<>();
// Create some products
private void createSomeData() {
    Article article1 = new Article("Fallacies of distributed computing", "https://en.wikipedia.org/wiki/Fallacies_of_distributed_computing");
    products.put(article1.getId(), article1);
    Article article2 = new Article("Reactive Manifesto", "https://www.reactivemanifesto.org/");
    products.put(article2.getId(), article2);
}
```

Then, in the `start` method, call the `createSomeData` method:

```java
@Override
public void start(Future<Void> fut) {

  createSomeData();

  // Create a router object.
  Router router = Router.router(vertx);

  // Rest of the method
}
```

As you have noticed, we don’t really have a backend here, it’s just a (in-memory) map. Adding a backend will be covered in another post.

### Get our products

Enough decoration, let’s implement the REST API. We are going to start with `GET /api/articles`. It returns the list of articles structured in a JSON Array.

In the `start` method, add this line just below the static handler line:

```java
router.get("/api/articles").handler(this::getAll);
```

This line instructs the router to handle the `GET` requests on `/api/articles` by calling the getAll method. We could have inlined the handler code, but for clarity reasons let’s create another method:

```java
private void getAll(RoutingContext routingContext) {
  routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily(products.values()));
}
```

Like every route handler, our method receives a `RoutingContext`. We populate the response by setting the `content-type` header and the actual content. To create the actual content, no need to compute the JSON string ourself. Vert.x provices the `Json` class mapping object to and from JSON String. So `Json.encodePrettily(products.values())` computes the JSON string representing the set of articles.

We could have used `Json.encodePrettily(products`), but to make the JavaScript code simpler, we just return the set of articles and not an object containing `ID => Article` entries.

With this in place, we should be able to retrieve the set of bottle from our HTML page. Let’s try it:

```
mvn compile vertx:run
```

Then open the HTML page in your browser to (http://localhost:8082/assets/index.html), and should should see:

![the main application view](articles-list "The main application view")

You may wonder why the port is 8082? Remember, in the previous post we created the `src/main/conf/my-application-conf.json`. The Vert.x Maven Plugin takes this file as configuration by default. 

I’m sure you are curious, and want to actually see what is returned by our REST API. Let’s open a browser to http://localhost:8082/api/articles. You should get:

```json
[ {
  "id" : 0,
  "title" : "Fallacies of distributed computing",
  "url" : "https://en.wikipedia.org/wiki/Fallacies_of_distributed_computing"
}, {
  "id" : 1,
  "title" : "Reactive Manifesto",
  "url" : "https://www.reactivemanifesto.org/"
} ]
```

### Create a product

Now we can retrieve the set of articles, let’s create a new one. Unlike the previous REST API endpoint, this one needs to read the request’s body. For performance reason, it should be explicitly enabled. Don’t be scared... it’s just a handler.

In the start method, add these lines just below the line ending by `getAll`:

```java
router.route("/api/articles*").handler(BodyHandler.create());
router.post("/api/articles").handler(this::addOne);
```

The first line enables the reading of the request body for all routes under `/api/articles`. We could have enabled it globally with `router.route().handler(BodyHandler.create())`.

The second line maps POST requests on `/api/articles` to the `addOne` method. Let’s create this method:

```java
private void addOne(RoutingContext routingContext) {
    Article article = routingContext.getBodyAsJson().mapTo(Article.class);
    products.put(article.getId(), article);
    routingContext.response()
        .setStatusCode(201)
        .putHeader("content-type", "application/json; charset=utf-8")
        .end(Json.encodePrettily(article));
}
```

The method starts by creating an `Article` instance from the request body. Once created it adds it to the backend and returns the created article as JSON.

Let’s try this, if you kept the application running just click on the `Add a new article` button. If not: `mvn compile vertx:run`.

Enter the data such as: `Building Reactive Microservices in Java` as title and `https://developers.redhat.com/promotions/building-reactive-microservices-in-java/` as url. Click on `save`, and the article should appear in the list.

_Status 201?_
As you can see, we have set the response status to `201`. It means `CREATED`, and is the generally used in REST API that create an entity. By default vert.x web is setting the status to `200` meaning `OK`.

### Reading complete

Well, sometimes you take time to read articles, so we should be able to delete it from the list. In the `start` method, add this line:

```java
router.delete("/api/articles/:id").handler(this::deleteOne);
```

In the _path_, we define a parameter `:id`. So, when handling a matching request, Vert.x extracts the path segment corresponding to the parameter and let us access it in the handler method. For instance, `/api/articles/0` maps `id` to `0`.

Let’s see how the parameter can be used in the handler method. Create the deleteOne method as follows:

```java
private void deleteOne(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");
    try {
        Integer idAsInteger = Integer.valueOf(id);
        products.remove(idAsInteger);
        routingContext.response().setStatusCode(204).end();
    } catch (NumberFormatException e) {
        routingContext.response().setStatusCode(400).end();
    }
}
```

The path parameter is retrieved using `routingContext.request().getParam("id")`. In a `try-catch` block we try to convert this path parameter as integer. If it fails (with a `NumberFormatException`), we write a `Bad Request - 400` response. If it's ok, we just remove the article from our backend.

_Status 204?_
As you can see, we have set the response status to `204 - NO CONTENT`. Responses to the HTTP Verb `DELETE` have generally no content.

### The other methods

We won’t explain `getOne` and `updateOne` as the implementations are straightforward and very similar. Their implementations are available on GitHub.

## Concurrency

Let's talk a bit about concurrency. Obviously using an in-memory backend in not a production settings, but it illustrates one of the key characteristic of Vert.x. We do read and write operations on this backend without using any synchronization constructs. Seasonned Java developers would cleary be mad about this... 

However, Vert.x verticles are single threaded. It means that only one thread is accessing them, and always the same. So we don't need synchronization because we can't have concurrent accesses. That's great, isn't it? But how do we handle concurrent HTTP requests? Well, that's also simple, using the very same thread everytime. Because everything we do is not blocking processing and respding to the request is fast, so while we won't process another request at the **same** time, it does not mean we can't handle concurrent requests, they are just queued, but not for long. If you try to execute concurrent requests (with tools like Gatling or `wrk`) you will realize very good response time, thanks to this event loop mechanism.

### Summary

It’s time to conclude this post. We have seen how Vert.x Web lets you implement a REST API easily and how it can serve static resources. A bit more fancy than before, but still pretty easy.

In the next post we are going to use a PostGres database as backend.

Say Tuned & Happy Coding !