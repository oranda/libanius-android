resolvers += Resolver.url("scalasbt releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers ++= Seq("snapshots" at "http://oss.sonatype.org/content/repositories/snapshots", 
		  "releases"  at "http://oss.sonatype.org/content/repositories/releases")


addSbtPlugin("com.hanhuy.sbt" % "android-sdk-plugin" % "1.3.24")



