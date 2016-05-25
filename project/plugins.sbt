import _root_.sbt._

resolvers += Resolver.url("scalasbt releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

//resolvers += Resolver.url("scalasbt snapshots", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns)

resolvers ++= Seq("snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
	"releases"  at "http://oss.sonatype.org/content/repositories/releases")


addSbtPlugin("org.scala-sbt" % "sbt-android-plugin" % "0.6.2")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")
