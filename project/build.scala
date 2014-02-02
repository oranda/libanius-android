import sbt._

import Keys._
import AndroidKeys._

object General {

  val settings = Defaults.defaultSettings ++ Seq (
    name := "Libanius",
    version := "0.951",
    versionCode := 951,
    scalaVersion := "2.10.2",
    platformName in Android := "android-14",  // formerly android-8
    scalacOptions += "-deprecation",
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
    parallelExecution in Test := false,
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    libraryDependencies ++= Seq(       //"com.typesafe.config" % "config" % "0.3.0",
                                "com.typesafe.akka" % "akka-actor_2.10" % "2.1.0",
                                "org.scalaz" %% "scalaz-core" % "7.0.3",
                                "com.typesafe.play" %% "play-json" % "2.2.0-RC1",
                                "com.fasterxml.jackson.core" % "jackson-databind" % "2.2.2",
                                "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.2.2"),
    unmanagedClasspath in Runtime <+= (baseDirectory) map { bd => Attributed.blank(bd / "config") }
  )

  val proguardSettings = Seq (
    useProguard in Android := true  // enable for deploy to device
  )

  val proguardOptions = Seq(
    proguardOption in Android :=
      """
        |-keep class com.typesafe.**
        |-keep class akka.**
        |-keep class com.fasterxml.jackson.** { *; }
        |
        |-keep class scala.collection.immutable.StringLike {*;}
        |
        |-keepclasseswithmembers class * {public <init>(java.lang.String, akka.actor.ActorSystem$Settings, akka.event.EventStream, akka.actor.Scheduler, akka.actor.DynamicAccess);}
        |-keepclasseswithmembers class * {public <init>(akka.actor.ExtendedActorSystem);}
        |-keep class scala.collection.SeqLike {public protected *;}
        |
        |-keep public class * extends android.app.Application
        |-keep public class * extends android.app.Service
        |-keep public class * extends android.content.BroadcastReceiver
        |-keep public class * extends android.content.ContentProvider
        |-keep public class * extends android.view.View {public <init>(android.content.Context);
        | public <init>(android.content.Context, android.util.AttributeSet); public <init>
        | (android.content.Context, android.util.AttributeSet, int); public void set*(...);}
        |-keepclasseswithmembers class * {public <init>(android.content.Context, android.util.AttributeSet);}
        |-keepclasseswithmembers class * {public <init>(android.content.Context, android.util.AttributeSet, int);}
        |-keepclassmembers class * extends android.content.Context {public void *(android.view.View); public void *(android.view.MenuItem);}
        |-keepclassmembers class * implements android.os.Parcelable {static android.os.Parcelable$Creator CREATOR;}
        |-keepclassmembers class **.R$* {public static <fields>;}
      """.stripMargin
  )


  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    TypedResources.settings ++
    proguardSettings ++
    proguardOptions ++
    AndroidManifestGenerator.settings ++
    AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "alias_name",
      libraryDependencies ++= Seq("org.specs2" %% "specs2" % "2.0" % "test")
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "Libanius",
    file("."),
    settings = General.fullAndroidSettings
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings ++
               AndroidTest.settings ++
               General.proguardSettings ++ Seq (
      name := "LibaniusTests"    
      //resolvers += robospecsReleases,
      //libraryDependencies += robospecs
    )
  ) dependsOn main

}
