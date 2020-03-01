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