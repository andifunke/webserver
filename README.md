# WebServer in Java

My solution to an assignment from the computer network class back in *2015*.
The task was to write a basic web server based on a given scaffold.
The server supports mime-types and HTTP/1.0 conform requests with valid responses.
The server can be accessed on port *6789*.

### Compilation

```
$ javac src/WebServer.java -d ./WebServer/
```

### Run Server

```
$ cd WebServer
$ java WebServer
```

You may specify a different location for the mime.type table via

```
$ java WebServer -mime path/to/mime.types
```


### Send request

From your browser:

```
http://localhost:6789/index.html
```
