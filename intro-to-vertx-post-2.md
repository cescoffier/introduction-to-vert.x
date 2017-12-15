# Vert.x Application Configuration

## Previously in ‘Introduction to Vert.x’

In the previous post, we developed a very simple Vert.x application, and saw how this application can be tested, packaged and executed. That was nice, isn’t it? Well, ok, that was only the beginning. In this post, we are going to enhance our application to support external configuration.

So just to remind you, we have an application starting a HTTP server on the port `8080` and replying a polite “Hello” message to all HTTP requests. The code of the previous post is available in the `post-1` directory from https://github.com/cescoffier/introduction-to-vert.x. The code developed in this post in in the `post-2` directory.

## So, why do we need configuration?

That’s a good question. The application works right now, but well, let’s say you want to deploy it on a machine where the port `8080` is already taken. We would need to change the port in the application code and in the test, just for this machine. That would be sad. Fortunately, Vert.x applications are configurable.

They are several ways to configure a Vert.x application:

1. using a simple JSON file
2. using Vert.x Config

In both case, the application code manipulates the configuration as a `JsonObject`. 

When using a simple JSON file, the verticle receives the configuration. The configuration can be passed in the command line, or using an API. Let’s have a look.

## No ‘8080’ anymore

The first step is to modify the `io.vertx.intro.first.MyFirstVerticle` class to not bind to the port `8080`, but to read it from the configuration:

```java
package io.vertx.intro.first;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

public class MyFirstVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> fut) {
        vertx
            .createHttpServer()
            .requestHandler(r ->
                r.response().end("<h1>Hello from my first Vert.x application</h1>"))
            .listen(config().getInteger("HTTP_PORT", 8080), result -> {
                if (result.succeeded()) {
                    fut.complete();
                } else {
                    fut.fail(result.cause());
                }
            });
    }
}
```

So, the only difference with the previous version is `config().getInteger("HTTP_PORT", 8080)`. Here, our code is now requesting the configuration and check whether the `HTTP_PORT` property is set. If not, the port `8080` is used as fallback. The retrieved configuration is a `JsonObject`.

As we are using the port `8080` by default, you can still package our application and run it as before:

```
mvn clean package
java -jar target/my-first-app-1.0-SNAPSHOT.jar
```

Simple right ?

## API-based configuration - Random port for the tests

Now that the application is configurable, let’s try to provide a configuration. In our test, we are going to configure our application to use the port `8081`. So, previously we were deploying our verticle with:

```java
vertx.deployVerticle(MyFirstVerticle.class.getName(), context.asyncAssertSuccess());
```

Let’s now pass some deployment options:

```java
private Vertx vertx;
// New field storing the port.
private int port = 8081;

@Before
public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    // Create deployment options with the chosen port
    DeploymentOptions options = new DeploymentOptions()
        .setConfig(new JsonObject().put("HTTP_PORT", port));
    // Deploy the verticle with the deployment options
    vertx.deployVerticle(MyFirstVerticle.class.getName(), options, context.asyncAssertSuccess());
}
```

The `DeploymentOptions` object lets us customize various parameters. In particular, it lets us inject the `JsonObject` retrieved by the verticle when using the `config()` method.

Obviously, the test connecting to the server needs to be slightly modified to use the right port (port is a field):

```java
vertx.createHttpClient().getNow(port, "localhost", "/", response -> {
  response.handler(body -> {
    context.assertTrue(body.toString().contains("Hello"));
    async.complete();
  });
});
```

Ok, well, this does not really fix our issue. What happens when the port `8081` is used too. Let’s now pick a random port:

```java
// Pick an available and random
ServerSocket socket = new ServerSocket(0);
port = socket.getLocalPort();
socket.close();

DeploymentOptions options = new DeploymentOptions()
            .setConfig(new JsonObject().put("HTTP_PORT", port));
vertx.deployVerticle(MyFirstVerticle.class.getName(), options, context.asyncAssertSuccess());
```

So, the idea is very simple. We open a server socket that would pick a random port (that’s why we put `0` as parameter). We retrieve the used port and close the socket. Be aware that this method is not perfect and may fail if the picked port becomes used between the `close` method and the start of our HTTP server. However, it would work fine in the very high majority of the case.

With this in place, our test is now using a random port. Execute them with:

```bash
mvn clean test
```

## External configuration - Let’s run on another port

Ok, well random port is not what we want in production. Could you imagine the face of your ops team if you tell them that your application is picking a random port. It can actually be funny, but we should never mess with the ops team.

So for the actual execution of your application, let’s pass the configuration in an external file. The configuration is stored in a json file.

Create the `src/main/conf/my-application-conf.json` with the following content:

```json
{
  "HTTP_PORT" : 8082
}
```

And now, to use this configuration just launch your application with:

```
java -jar target/my-first-app-1.0-SNAPSHOT.jar -conf src/main/conf/my-application-conf.json
```

Open a browser on http://localhost:8082, here it is!

How does that work? Our fat jar is using the `Launcher` class (provided by Vert.x) to launch our application. This class is reading the `-conf` parameter and create the corresponding deployment options when deploying our verticle.

## 12 Factor Apps and other configuration store

While storing the configuration in a JSON file is pretty convenient, it does not always fit the requirement. For instance, if you follows the [12 Factor App](https://12factor.net/config) principles, it recommend the application to read _environment variables_ as configuration. What about Consul, or Vault to store _secrets_. To handle all these case, Vert.x provides a convenient module named: `vertx-config`. In this section, we change how we retrieve the HTTP port from the environment variable, system properties and finally the provided configuration file.

First, add the following dependency to your `pom.xml` file:

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-config</artifactId>
    <version>${vertx.version}</version>
</dependency>
```    

In your verticle class, update the content of the `start` method to become:

```java
@Override
public void start(Future<Void> fut) {
    ConfigRetriever retriever = ConfigRetriever.create(vertx);
    retriever.getConfig(
        config -> {
            if (config.failed()) {
                fut.fail(config.cause());
            } else {
                vertx
                    .createHttpServer()
                    .requestHandler(r ->
                        r.response().end("<h1>Hello from my first Vert.x application</h1>"))
                    .listen(config.result().getInteger("HTTP_PORT", 8080), result -> {
                        if (result.succeeded()) {
                            fut.complete();
                        } else {
                            fut.fail(result.cause());
                        }
                    });
            }
        }
    );
}
```    

The `vertx-config` module provides the `ConfigRetriever`. This object is responsible for retrieving the different configuration chunk and compute the final configuration. This process being asynchronous, the result is passed to a handler that execute the rest of the startup logic.

With this in place, the port is now chosen from 3 different locations:

* the configuration file as seen previously (using `-conf`)
* system properties. For instance, launch the application with `-DHTTP_PORT=8081` to use the port 8081
* environment properties. For instance, launch the application with:

```
export HTTP_PORT=8081
java -jar target/my-first-app-1.0-SNAPSHOT.jar
```

`vertx-config` proposes a lot more features and config store. Check its [documentation](http://vertx.io/docs/vertx-config/java/).

## Conclusion

After having developed your first Vert.x application, we have seen how this application is configurable, and this without adding complexity to our application. In the next post, we are going to see how we can use `vertx-web` to develop a small application serving static pages and a REST API. A bit more fancy, but still very simple.

Happy Coding and & Stay Tuned!