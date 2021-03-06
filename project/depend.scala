import sbt._
import Keys._

object depend {
  val scalaz    = Seq("org.scalaz"           %% "scalaz-core"     % "7.1.0",
                      "org.scalaz"           %% "scalaz-effect"   % "7.1.0")
  val scopt     = Seq("com.github.scopt"     %% "scopt"           % "3.2.0")
  val trove     = Seq("trove"                %  "trove"           % "1.0.2")
  val joda      = Seq("joda-time"            %  "joda-time"       % "2.1",
                      "org.joda"             %  "joda-convert"    % "1.1")
  val specs2    = Seq("org.specs2"           %% "specs2-core",
                      "org.specs2"           %% "specs2-junit",
                      "org.specs2"           %% "specs2-html",
                      "org.specs2"           %% "specs2-matcher-extra",
                      "org.specs2"           %% "specs2-scalacheck").map(_ % "2.4.5").map(_ % "test")
  val commonsio = Seq("commons-io"           %  "commons-io"      % "2.4")
  val thrift    = Seq("org.apache.thrift"    %  "libthrift"       % "0.9.1" excludeAll ExclusionRule(organization = "org.apache.httpcomponents"))
  val mundaneVersion = "1.2.1-20160108044905-83acfd2"
  val mundane   = Seq("com.ambiata"          %% "mundane-control",
                      "com.ambiata"          %% "mundane-path",
                      "com.ambiata"          %% "mundane-io").map(_ % mundaneVersion) ++
                  Seq("com.ambiata"          %% "mundane-testing",
                      "com.ambiata"          %% "mundane-path",
                      "com.ambiata"          %% "mundane-io").map(_ % mundaneVersion % "test->test")

  val shapeless = Seq("com.chuusai"          %% "shapeless"       % "2.0.0")
  val disorder =  Seq("com.ambiata"          %% "disorder"        % "0.0.1-20150219021345-bfcf0db" % "test")

  def scoobi(version: String) = {
    val jars =
      if (version == "mr1")              Seq("com.nicta" %% "scoobi"                    % "0.9.0-cdh4-20141017043441-0c9fb18",
                                             "com.nicta" %% "scoobi-compatibility-cdh4" % "1.0.3")
      else if (version == "yarn")        Seq("com.nicta" %% "scoobi"                    % "0.9.0-cdh5-20141017042745-0c9fb18",
                                             "com.nicta" %% "scoobi-compatibility-cdh5" % "1.0.3")
      else                               sys.error(s"unsupported scoobi version, can not build for $version")
    jars.map(_ intransitive())
  }

  def hadoop(version: String, hadoopVersion: String = "2.2.0") =
    if (version == "mr1")              Seq("org.apache.hadoop" % "hadoop-client" % "2.0.0-mr1-cdh4.6.0" % "provided" exclude("asm", "asm"),
                                           "org.apache.hadoop" % "hadoop-core"   % "2.0.0-mr1-cdh4.6.0" % "provided",
                                           "org.apache.avro"   % "avro-mapred"   % "1.7.4"              % "provided" classifier "hadoop2")

    else if (version == "yarn")        Seq("org.apache.hadoop" % "hadoop-client" % "2.2.0-cdh5.0.0-beta-2" % "provided" exclude("asm", "asm"),
                                           "org.apache.avro"   % "avro-mapred"   % "1.7.5-cdh5.0.0-beta-2" % "provided")

    else sys.error(s"unsupported hadoop version, can not build for $version")

  val resolvers = Seq(
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("public"),
      Resolver.typesafeRepo("releases"),
      "cloudera"             at "https://repository.cloudera.com/content/repositories/releases",
      "cloudera2"            at "https://repository.cloudera.com/artifactory/public",
      Resolver.url("ambiata-oss", new URL("https://ambiata-oss.s3.amazonaws.com"))(Resolver.ivyStylePatterns),
      Resolver.url("ambiata-oss-v2", new URL("https://ambiata-oss-v2.s3.amazonaws.com"))(Resolver.ivyStylePatterns),
      "Scalaz Bintray Repo"  at "http://dl.bintray.com/scalaz/releases")
}
