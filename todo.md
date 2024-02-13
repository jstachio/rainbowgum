
# Design questions

## Should appenders own and manage the output (stream, queue, socket or whatever)?

In Logback they do. The appender is essentially the output.

In Log4j2 they do not. Log4j2 has the concept of "managers" (I loathe that name)
which are independent of appenders that allow multiple appenders to a single 
logical resource (it might not be a single physical resource particularly in the case of rolling).

# Features in limbo

* Filters - medium
* Markers - We might use this for some other kind of features
* Key values that are not MDC aka new slf4j event builder
* Header and footer support

# Features not going to support at the moment

* Rolling


## TODO before release

- [ ] Level changing loggers - medium
- [X] A network based LogOutput. Probably AMQP.
- [ ] Fix LogProperties search to use interpolate key. 
- [ ] More JSON encoders.
- [ ] A console theme package based off of HL (logging colorizer).
- [ ] Remove all the field based formatters.
- [ ] Code samples and instructions for custom configuration.
- [ ] How to setup a ServiceLoader registration for plugins
- [ ] Scoped Value plugin
- [ ] JEP 430 String templates example
- [ ] Check performance again
- [ ] Remove bloat / unused code
- [ ] Check performance again
 
