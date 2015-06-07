import android.Keys._

name := "Libanius"

resolvers += "Typesafe Repository" at "http:repo.typesafe.com/typesafe/releases/"

javacOptions in Global ++= "-target" :: "1.7" :: "-source" :: "1.7" :: Nil

scalacOptions in Global += "-feature"

scalaVersion in Global := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor_2.10" % "2.1.0",
  "org.scalaz" %% "scalaz-core" % "7.1.2",
  "com.typesafe.play" %% "play-json" % "2.3.4",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.2",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.5.2"
)

proguardOptions in Android ++= Seq(
  "-dontwarn **",
  "-keep class com.typesafe.**",
  "-keep class akka.**",
  "-keep class com.fasterxml.jackson.** { *; }",
  "-keep class scala.collection.immutable.StringLike {*;}",
  "-keepclasseswithmembers class * {public <init>(java.lang.String, akka.actor.ActorSystem$Settings, akka.event.EventStream, akka.actor.Scheduler, akka.actor.DynamicAccess);}",
  "-keepclasseswithmembers class * {public <init>(akka.actor.ExtendedActorSystem);}",
  "-keep class scala.collection.SeqLike {public protected *;}",
  "-keep public class * extends android.app.Application",
  "-keep public class * extends android.app.Service",
  "-keep public class * extends android.content.BroadcastReceiver",
  "-keep public class * extends android.content.ContentProvider",
  "-keep public class * extends android.view.View {public <init>(android.content.Context);",
  " public <init>(android.content.Context, android.util.AttributeSet); public <init>" +
    " (android.content.Context, android.util.AttributeSet, int); public void set*(...);}",
  "-keepclasseswithmembers class * {public <init>(android.content.Context, android.util.AttributeSet);}",
  "-keepclasseswithmembers class * {public <init>(android.content.Context, android.util.AttributeSet, int);}",
  "-keepclassmembers class * extends android.content.Context {public void *(android.view.View); public void *(android.view.MenuItem);}",
  "-keepclassmembers class * implements android.os.Parcelable {static android.os.Parcelable$Creator CREATOR;}",
  "-keepclassmembers class **.R$* {public static <fields>;}"
)

apkbuildExcludes in Android ++= Seq(
   "META-INF/DEPENDENCIES",
   "META-INF/NOTICE",
   "META-INF/LICENSE",
   "META-INF/LICENSE.txt",
   "META-INF/NOTICE.txt")

proguardCache in Android ++= Seq(ProguardCache("com.typesafe.akka"), ProguardCache("org.scalaz"), ProguardCache("com.typesafe.play"), ProguardCache("com.fasterxml.jackson"))

