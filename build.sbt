lazy val paint =
  (project in file("paint"))
    .settings(BaseSettings.default(Some("legacy")))
    .withTestConfig(coverageMinimumPercent = 88)
    .withDependencies

lazy val server =
  (project in file("server"))
  .dependsOn(paint % "e2e->it;it->it;test->test;compile->compile")
  .settings(BaseSettings.default(Some("server")))
    .withTestConfig(coverageMinimumPercent = 88)
    .withDependencies

lazy val root =
  (project in file("."))
  .dependsOn(server, paint)
    .aggregate(server, paint)
    .settings(BaseSettings.default())
    .withDependencies