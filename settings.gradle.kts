rootProject.name = "kotlin-springboot"

rootDir
  .listFiles { f -> f.isDirectory && f.name.matches(Regex("""\d{2}-.*""")) }
  ?.sortedBy { it.name }
  ?.forEach { include(":${it.name}") }

include(":common-testutils")
