
# Design questions

## Should appenders own and manage the output (stream, queue, socket or whatever)?

In Logback they do. The appender is essentially the output.

In Log4j2 they do not. Log4j2 has the concept of "managers" (I loathe that name)
which are independent of appenders that allow multiple appenders to a single 
logical resource (it might not be a single physical resource particularly in the case of rolling).

# Features in limbo

* Level changing loggers - medium
* Filters - medium
* Markers - We might use this for some other kind of features
* Key values that are not MDC aka new slf4j event builder
* Header and footer support

# Features not going to support at the moment

* Rolling

