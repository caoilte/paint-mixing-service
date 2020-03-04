# API and SDK Solution

I will document my solution in this file as I go.

## Plan

- Part 1 : API Design and implementation
  - Pick technology stack [0m]
  - Research best way to run new stack and old stack simultaneously [30m]
  - Get legacy app working and accessible from new technology stack [30m]
  - Design ideal Domain Model for thinking about the request/response [30m]
  - Write and test client for legacy implementation [30m]
  - Design and document new API [30m]
  - Write and test HTTP API for new implementation [30m]
  - Write end-to-end test for new implementation [30m]
  - Get new implementation running as application [30m]
  - Get new and legacy implementation running side by side and test manually [30m]
  - Document solution and collate instructions for running it [30m]
- Part 2 : Authentication and Authorization
  - Research practical COTS product that can manage quotas [1h]
  - Setup demo account with different limits and quotas for different users [30m]
  - Write and test middleware for new implementation that can check access token [30m]
  - Write scripts for logging in / testing the new API with appropriate auth token [30m]
  - Document solution and collate instructions for running it [30m]
- Part 3 : History
  - Look for practical COTS product that can do this OOB [1h]
  - On assumption that there is no such product,
    - Choose suitable storage mechanism for recording history [30m]
    - Get storage mechanism working locally [30m]
    - Write and test client that can store history for a user id [1h]
    - Write and test client that retrieve history for a user [30m]
    - Write and test Middleware that stores history for a user based on their Authentication token [30m]
    - Design and document new API that can retrieve user history [30m]
    - Write and test HTTP API for retrieving history [30m]
    - Write and test fine-grained Authorization checks so user can only retrieve their own history [30m]
    - Write end-to-end test for recording and then retrieving history [1h]
    - Incorporate storage mechanism into existing stack so that it can easily be run with new and legacy stack [30m]
    - Document solution and collate instructions for running it [30m]

## Running Legacy App Locally

After playing around with Jython its pretty obvious that Docker is the way to go. To make a local Docker image go to the `legacy_app` directory and run,

`docker build -t legacy-paint .`

to make a local docker image. Then run it with,

`docker run --publish=8080:8080 -it --rm  --name legacy-paint-running legacy-paint`

You can test it in a separate terminal eg,

`curl "http://0.0.0.0:8080/v1/?input=%7B%22colors%22%3A5%2C%22customers%22%3A3%2C%22demands%22%3A%5B%5B1%2C1%2C1%5D%2C%5B2%2C1%2C0%2C2%2C0%5D%2C%5B1%2C5%2C0%5D%5D%7D"`

## Testing the New App

You can test the new app if you have `sbt` installed. Because this is how I write tests day-to-day for much larger projects you can run the two different types of tests once in the sbt terminal as follows,

```
it
e2e
```

## Running the New App in SBT

In the sbt terminal you can run the app with,

```
run
```

You can then test it from a separate terminal with,

```
curl --header "Content-Type: application/json"   --request POST   --data @invalid_request.json  http://localhost:9090/v2/optimize
```

You should get a pretty explanatory error message,

```
{
	"errors": [{
		"errorMessage": "Received customer number '3' but customer numbers must be 0-2'"
	}, {
		"errorMessage": "Customer '3' had paint mixes 'sparkly' but paint mixes must be 'matte' or 'glossy'"
	}]
}
```

## Running the New App in Docker

After publishing the application locally as a docker image with the following sbt command

```
docker:publishLocal
```

You should be able to run the entire stack with

```
docker-compose up
```

Some sample requests responses

### The happy path

```
curl --header "Content-Type: application/json"   --request POST   --data @happy_path.json  http://localhost:9090/v2/optimize
```

Will return (not so nicely formatted unless you use jq),

```
  "paintMixes": [
    {
      "colour": 1,
      "paintType": "Matte"
    },
    {
      "colour": 2,
      "paintType": "Glossy"
    },
    {
      "colour": 3,
      "paintType": "Glossy"
    },
    {
      "colour": 4,
      "paintType": "Glossy"
    },
    {
      "colour": 5,
      "paintType": "Glossy"
    }
  ]
}
```

### The unhappy path

```
curl -v --header "Content-Type: application/json"   --request POST   --data @sad_path.json  http://localhost:9090/v2/optimize
```

Will return a bunch of stuff and

```
< HTTP/1.1 404 Not Found
```

which is what I implemented for IMPOSSIBLE

## Problems I encountered

I kept a rough list of problems I ran into on this exercise which I've tidied up and which might be interesting,

- I naively thought I'd be able to make a nice quick Scala app that wrapped the Python with Jython. I very quickly discovered that trying to do this would be insane. 
  - Jython is not a very active project.
  - Scala wrapper for Jython hasn't been updated in 9 years
  - Goodness knows how I'd download the dependencies
- In disbelief I looked around for other options.
- Finally I remembered Docker. I think I was trying to avoid it and had a brain freeze.
- App wouldn't run with default Python Docker container because of [this bug](https://github.com/pyeve/eve/issues/1331) so I had to downgrade to Python 3.7.
- My Docker foo is very rusty. I completely forgot to publish ports!
- I stole basic sbt structure from a recent work project but it took a little longer to cut down to size than I first anticipated. Might have been better to start up from scratch instead.
- I also stole Docker testing approach from work, but I'd never looked into the detail of it before so had to spend quite a long time figuring out how it worked before I could get it to start correctly. Still not happy with the setup, but the result is agreeable.
- Encoding the domain model to the legacy request structure wasn't too bad but I knew I was already running out of time and the code is pretty ugly.
- Decoding the response was frustrating as I can't remember the last time I had to write a custom text decoder. 8 lines of code that took 30 minutes.
- I should have done a swagger doc really but I was really running out of time.
- I deliberately took some time to design a REST api structure that was easy to read even though it made it possible to encode invalid data that the domain model case class hierarchy prevented.
- This led to a rabbit hole of validation steps that was probably not the best use of my time for such an exercise (but is really nice)
- Running the app is something I haven't done in a while so was fiddly.
- Configuring the Scala docker publisher was a nightmare because I made the whole project a multimodule build and that needed a special configuration setting (aggregate false)
- Docker compose was, however, a dream....