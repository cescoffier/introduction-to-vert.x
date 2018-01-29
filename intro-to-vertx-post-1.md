# Introduction to Vert.x - My first Vert.x application

Let’s say, you heard someone saying that Eclipse Vert.x is awesome. Ok great, but you may want to try it by yourself. Well, the next natural question is “where do I start ?”. This post is a good starting point. It shows how to build a very simple vert.x application (nothing fancy), how it is tested and how it is packaged and executed. So, everything you need to know before building your own groundbreaking application.

The code developed in this post is available on github. This post is part of the _Introduction to Vert.x_ series. The code of this post is located in the https://github.com/cescoffier/introduction-to-vert.x repository in the `post-1` directory.

## Let’s start!

First, let’s create a project. In this post, we use Apache Maven, but you can use Gradle or the build process tool you prefer. You could use the Maven jar archetype to create the structure, but basically, you just need a directory with:

    * a `src/main/java` directory
    * a `src/test/java` directory
    * a `pom.xml` file

So, you would get something like:

```
.
├── pom.xml
├── src
│   ├── main
│   │   └── java
│   └── test
│       └── java
```

Let’s create the `pom.xml` file with the following content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>io.vertx.intro</groupId>
  <artifactId>my-first-app</artifactId>
  <version>1.0-SNAPSHOT</version>

  <properties>
    <vertx.version>3.5.0</vertx.version>
    <vertx-maven-plugin.version>1.0.13</vertx-maven-plugin.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.vertx</groupId>
      <artifactId>vertx-core</artifactId>
      <version>${vertx.version}</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.7.0</version>
        <configuration>
          <source>1.8</source>
          <target>1.8</target>
        </configuration>
      </plugin>
      <plugin>
        <groupId>io.fabric8</groupId>
        <artifactId>vertx-maven-plugin</artifactId>
        <version>${vertx-maven-plugin.version}</version>
        <executions>
          <execution>
            <id>vmp</id>
            <goals>
              <goal>initialize</goal>
              <goal>package</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <redeploy>true</redeploy>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
```

This `pom.xml` file is pretty straightforward:

    * it declares a dependency on `vertx-core`, the Vert.x version is declared as a property
    * it configures the `maven-compiler-plugin` to use Java 8.
    * it declares the `vertx-maven-plugin` - we will come back to this in a few second.

Vert.x requires at least Java 8, so don't try to run Vert.x application on a JAva 6 or 7 JVM, it won't work.

The `vertx-maven-plugin` is an optional plugin that packages your app and provides additional functionality (documentation is [here](https://vmp.fabric8.io/)). Otherwise you can use the `maven-shade-plugin` or any other packaging plugin. The `vertx-maven-plugin` is just convenient as it packages the application without any configuration. It also provides a _redeploy_ feature restarting the application when you update the code.

## Let’s code!

Ok, now we have made the `pom.xml` file. Let’s do some real coding... Create the `src/main/java/io/vertx/intro/first/MyFirstVerticle.java` file with the following content:

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
            .listen(8080, result -> {
                if (result.succeeded()) {
                    fut.complete();
                } else {
                    fut.fail(result.cause());
                }
            });
    }
}
```

This is actually our _not fancy_ application. The class extends `AbstractVerticle`. In the Vert.x world, a _verticle_ is a component. By extending `AbstractVerticle`, our class gets access to the `vertx` field, the `vertx` instance on which the verticle is deployed.

The `start` method is called when the verticle is deployed. We could also implement a `stop` method (called when the verticle is undeployed), but in this case Vert.x takes care of the garbage for us. The `start` method receives a `Future` object that lets us notify Vert.x when our start sequence has been completed or reports an error if anything bad happens. One of the particularities of Vert.x is its asynchronous and non-blocking aspect. When our verticle is going to be deployed it won’t wait until the `start` method has been completed. So, the `Future` parameter is important for notification of the completion. Notice that you can also implement a version of the `start` method without the `Future` parameter. In this case, Vert.x considers the verticle deployed when the `start` method returns.

The `start` method creates an HTTP server and attaches a _request handler_ to it. The request handler is a function (here a lambda), passed into the `requestHandler` method, and is called every time the server receives a request. In this code, we just reply _Hello_... (nothing fancy I told you). Finally, the server is bound to the `8080` port. As this may fail (because the port may already be used), we pass another lambda expression called with the result (and so able to check whether or not the connection has succeeded). As mentioned above it calls either `fut.complete` in case of success or `fut.fail` to report an error.

Before trying the application, edit the `pom.xml` file and add the `vertx.verticle` entry in the properties:

```xml
<properties>
    <vertx.version>3.5.0</vertx.version>
    <vertx-maven-plugin.version>1.0.13</vertx-maven-plugin.version>
    <!-- line to add: -->
    <vertx.verticle>io.vertx.intro.first.MyFirstVerticle</vertx.verticle>
</properties>
```  

This property instructs Vert.x to deploy this class when it starts.

Let’s try to compile the application using:

```bash
mvn compile vertx:run
```

The application is started, open your browser to `http://localhost:8080` and you should see the _Hello_ message. If you change the message in the code and save the file, the application is restarted with the updated message.

Hit `CTRL+C` to stop the application.

That’s all for the application.

## Let’s test

Well, that’s good to have developed an application, but we can never be too careful, so let’s test it. The test uses JUnit and `vertx-unit` - a framework delivered with vert.x to make the testing of vert.x application more natural.

Open the pom.xml file to add the two following dependencies:

<dependency>
  <groupId>junit</groupId>
  <artifactId>junit</artifactId>
  <version>4.12</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-unit</artifactId>
  <version>${vertx.version}</version>
  <scope>test</scope>
</dependency>

Now create the `src/test/java/io/vertx/intro/first/MyFirstVerticleTest.java` with the following content:

```java
package io.vertx.intro.first;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MyFirstVerticleTest {

    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.deployVerticle(MyFirstVerticle.class.getName(),
            context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void testMyApplication(TestContext context) {
        final Async async = context.async();

        vertx.createHttpClient().getNow(8080, "localhost", "/",
            response ->
                response.handler(body -> {
                    context.assertTrue(body.toString().contains("Hello"));
                    async.complete();
                }));
    }
}
```

This is a JUnit test case for our verticle. The test uses `vertx-unit`, so we configure a custom runner (with the `@RunWith` annotation). `vertx-unit` makes easy to test asynchronous interactions, which are the basis of vert.x applications.

In the `setUp` method (called before each test), we create an instance of `vertx` and deploy our verticle. You may have noticed that unlike the traditional JUnit `@Before` method, it receives a `TestContext` object. This object lets us control the asynchronous aspect of our test. For instance, when we deploy our verticle, it starts asynchronously, as do most Vert.x interactions. We cannot check anything until it gets started correctly. So, as second argument of the `deployVerticle` method, we pass a result handler: `context.asyncAssertSuccess()`. It fails the test if the verticle does not start correctly. In addition it waits until the verticle has completed its start sequence. Remember, in our verticle, we call `fut.complete()`. So it waits until this method is called, and in the case of a failures, fails the test.

Well, the `tearDown` (called after each test) method is straightforward, and just closes the `vertx` instance we created.

Let’s now have a look at the test of our application: the `testMyApplication` method. The test emits a request to our application and checks the result. Emitting the request and receiving the response is asynchronous. So we need a way to control this. Like the `setUp` and `tearDown` methods, the test method receives a `TestContext`. From this object we create an async handle (`async`) that lets us notify the test framework when the test has completed (using `async.complete()`).

So, once the async handle is created, we create an HTTP client and emit an HTTP request handled by our application with the `getNow()` method (`getNow` is just a shortcut for `get(...).end()`). The response is handled by a handler. In this function, we retrieve the response body by passing another function to the handler method. The `body` argument is the response body (as a `buffer` object). We check that the body contains`"Hello"` and declare the test complete.

Let’s take a minute to mention the assertions. Unlike in traditional JUnit tests, it uses `context.assert`.... Indeed, if the assertion fails, it will interrupt the test immediately. So it’s important to use these assertions because of the asynchronous aspect of the Vert.x application and  tests. However, Vert.x Unit provides hooks to let you use Hamcrest or AssertJ as demonstrated in this [example](https://github.com/vert-x3/vertx-examples/blob/master/unit-examples/src/test/java/io/vertx/example/unit/test/JUnitAndHamcrestTest.java) and this other [example](https://github.com/vert-x3/vertx-examples/blob/master/unit-examples/src/test/java/io/vertx/example/unit/test/JUnitAndAssertJTest.java).

Our test can be run from an IDE, or using Maven:

```
mvn clean test
```

## Packaging

So, let’s sum up. We have an application and a test. Well, let’s now package the application. In this post we package the application in a _fat jar_. A fat jar is a standalone executable Jar file containing all the dependencies required to run the application. This is a very convenient way to package Vert.x applications as it’s only one file. It also make them easy to execute.

To create a fat jar, just run:

```
mvn package
```

The `vertx-maven-plugin` is taking care of the packaging and creates the `target/my-first-app-1.0-SNAPSHOT.jar` file embedding our application along with all the dependencies (including vert.x itself). Check the size: arond 6MB... and this includes everything to run the application.

## Executing our application

Well, it’s nice to have a jar, but we want to see our application running! As said above, thanks to the fat jar packaging, running Vert.x application is easy as:

```
java -jar target/my-first-app-1.0-SNAPSHOT.jar
```

Then, open a browser to http://localhost:8080.

To stop the application, hit CTRL+C.

## Conclusion

This Vert.x _crash class_ has presented how you can develop a simple application using Vert.x, how to test it, package it and run it. So, you now have a great start on the road to building an amazing system on top of Vert.x. Next time we will see how to configure our application.

Happy coding & Stay tuned!