# Description

This project contains unit tests to reproduce the issue with handling `null` values produced to Apache Pulsar topic that should represent tombstones (deletions of entities). 

[BUG #4804](https://github.com/apache/pulsar/issues/4804) described the issue and [enhancement #7139](https://github.com/apache/pulsar/pull/7139) solved it.

The solution included a `nullValue` flag inside message metadata used by [MessageIml](https://github.com/apache/pulsar/blob/master/pulsar-client/src/main/java/org/apache/pulsar/client/impl/MessageImpl.java) to indicate the presence of a `null` value in the message to avoid calling Avro parsing code and directly return `null` when calling `Message.getValue()`.  

Solution however does not work for two reasons:
* When trying to read a message with `null` value a `NullPointerException` is thrown in other part of the code 
* It should not be required to explicitly indicate a `null` value to producer (only-key values should work just just fine). Exception thrown when working with implicit `null` value messages is `EOFException` in this case

# Run the tests

An Apache Pulsar instance (2.6.0) must be running locally. If Docker Compose is installed on your computer (on Windows/Mac Docker Desktop will do) you can use provided [docker-comopose.yml](src/test/resources/docker-compose.yml) file:

```
$ cd src/test/resources
$ docker-compose up -d
```

To run the tests you can use your IDE or Maven from command line:

```
$ mvn test
```
