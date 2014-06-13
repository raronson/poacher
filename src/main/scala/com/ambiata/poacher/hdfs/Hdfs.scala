package com.ambiata.poacher.hdfs

import scalaz._, Scalaz._, \&/._, effect._, Effect._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, FileUtil, Path}
import java.io._

import com.ambiata.mundane.control._
import com.ambiata.mundane.io.{FilePath, Streams}
import com.nicta.scoobi.impl.util.Compatibility

case class Hdfs[+A](action: ActionT[IO, Unit, Configuration, A]) {
  def run(conf: Configuration): ResultTIO[A] =
    action.executeT(conf)

  def map[B](f: A => B): Hdfs[B] =
    Hdfs(action.map(f))

  def flatMap[B](f: A => Hdfs[B]): Hdfs[B] =
    Hdfs(action.flatMap(a => f(a).action))

  def mapError(f: These[String, Throwable] => These[String, Throwable]): Hdfs[A] =
    Hdfs(action.mapError(f))

  def mapErrorString(f: String => String): Hdfs[A] =
    Hdfs(action.mapError(_.leftMap(f)))

  def |||[AA >: A](other: Hdfs[AA]): Hdfs[AA] =
    Hdfs(action ||| other.action)
}

object Hdfs extends ActionTSupport[IO, Unit, Configuration] {

  def value[A](a: A): Hdfs[A] =
    Hdfs(super.ok(a))

  def ok[A](a: A): Hdfs[A] =
    value(a)

  def safe[A](a: => A): Hdfs[A] =
    Hdfs(super.safe(a))

  def fail[A](e: String): Hdfs[A] =
    Hdfs(super.fail(e))

  def fromDisjunction[A](d: String \/ A): Hdfs[A] = d match {
    case -\/(e) => fail(e)
    case \/-(a) => ok(a)
  }

  def fromValidation[A](v: Validation[String, A]): Hdfs[A] =
    fromDisjunction(v.disjunction)

  def fromIO[A](io: IO[A]): Hdfs[A] =
    Hdfs(super.fromIO(io))

  def fromResultTIO[A](res: ResultTIO[A]): Hdfs[A] =
    Hdfs(super.fromIOResult(res.run))

  def filesystem: Hdfs[FileSystem] =
    Hdfs(reader((c: Configuration) => FileSystem.get(c)))

  def configuration: Hdfs[Configuration] =
    Hdfs(reader(identity))

  def exists(p: FilePath): Hdfs[Boolean] =
    exists(new Path(p.path))

  def exists(p: Path): Hdfs[Boolean] =
    filesystem.map(fs => fs.exists(p))

  def isDirectory(p: FilePath): Hdfs[Boolean] =
    isDirectory(new Path(p.path))

  def isDirectory(p: Path): Hdfs[Boolean] =
    filesystem.map { fs =>
      try Compatibility.isDirectory(fs.getFileStatus(p))
      catch { case _: FileNotFoundException => false }
    }

  def mustexist(p: FilePath): Hdfs[Unit] =
    mustexist(new Path(p.path))

  def mustexist(p: Path): Hdfs[Unit] =
    exists(p).flatMap(e => if(e) Hdfs.ok(()) else Hdfs.fail(s"$p doesn't exist!"))

  def globPaths(p: FilePath): Hdfs[List[Path]] =
    globPaths(new Path(p.path))

  def globPathsWithGlob(p: FilePath, glob: String): Hdfs[List[Path]] =
    globPaths(new Path(p.path), glob)

  def globPaths(p: Path, glob: String = "*"): Hdfs[List[Path]] =
    filesystem.map(fs =>
      if(fs.isFile(p)) List(p) else fs.globStatus(new Path(p, glob)).toList.map(_.getPath)
    )

  def globPathsRecursively(p: FilePath): Hdfs[List[Path]] =
    globPathsRecursively(new Path(p.path))

  def globPathsRecursivelyWithGlob(p: FilePath, glob: String): Hdfs[List[Path]] =
    globPathsRecursively(new Path(p.path), glob)

  def globPathsRecursively(p: Path, glob: String = "*"): Hdfs[List[Path]] = {
    def getPaths(path: Path): FileSystem => List[Path] = { fs: FileSystem =>
      if (fs.isFile(path)) List(path)
      else {
        val paths = fs.globStatus(new Path(path, glob)).toList.map(_.getPath)
        (paths ++ paths.flatMap(p1 => fs.listStatus(p1).flatMap(s => getPaths(s.getPath)(fs)))).distinct
      }
    }
    filesystem.map(getPaths(p))
  }

  def globFiles(p: FilePath): Hdfs[List[Path]] =
    globFiles(new Path(p.path))

  def globFilesWithGlob(p: FilePath, glob: String): Hdfs[List[Path]] =
    globFiles(new Path(p.path), glob)

  def globFilesRecursively(p: FilePath): Hdfs[List[Path]] =
    globFiles(new Path(p.path))

  def globFilesRecursivelyWithGlob(p: FilePath, glob: String): Hdfs[List[Path]] =
    globFilesRecursively(new Path(p.path), glob)

  def globFiles(p: Path, glob: String = "*"): Hdfs[List[Path]] = for {
    fs    <- filesystem
    files <- globPaths(p, glob)
  } yield files.filter(fs.isFile)

  def globFilesRecursively(p: Path, glob: String = "*"): Hdfs[List[Path]] = for {
    fs    <- filesystem
    files <- globPathsRecursively(p, glob)
  } yield files.filter(fs.isFile)

  def readFromStream[A](p: FilePath, f: InputStream => ResultT[IO, A]): Hdfs[A] =
    readWith(new Path(p.path), f)

  def readFromStreamWithGlob[A](p: FilePath, f: InputStream => ResultT[IO, A], glob: String): Hdfs[A] =
    readWith(new Path(p.path), f, glob)

  def readWith[A](p: Path, f: InputStream => ResultT[IO, A], glob: String = "*"): Hdfs[A] = for {
    _     <- mustexist(p)
    paths <- globFiles(p, glob)
    a     <- filesystem.flatMap(fs => {
               if(!paths.isEmpty) {
                 val is = paths.map(fs.open).reduce[InputStream]((a, b) => new SequenceInputStream(a, b))
                 Hdfs.fromResultTIO(ResultT.using(ResultT.safe[IO, InputStream](is)) { in =>
                   f(is)
                 })
               } else {
                 Hdfs.fail[A](s"No files found for path $p!")
               }
             })
  } yield a

  def readContentAsString(p: FilePath): Hdfs[String] =
    readContentAsString(new Path(p.path))

  def readContentAsString(p: Path): Hdfs[String] =
    readWith(p, Streams.read(_: InputStream))

  def readLines(p: FilePath): Hdfs[Iterator[String]] =
    readLines(new Path(p.path))

  def readLines(p: Path): Hdfs[Iterator[String]] =
    readContentAsString(p).map(_.lines)

  def globLines(p: FilePath): Hdfs[Iterator[String]] =
    globLines(new Path(p.path))

  def globLinesWithGlob(p: FilePath, glob: String): Hdfs[Iterator[String]] =
    globLines(new Path(p.path))

  def globLines(p: Path, glob: String = "*"): Hdfs[Iterator[String]] =
    Hdfs.globFiles(p, glob).flatMap(_.map(Hdfs.readLines).sequenceU.map(_.toIterator.flatten))

  def writeToStream[A](p: FilePath, f: OutputStream => ResultT[IO, A]): Hdfs[A] =
    writeWith(new Path(p.path), f)

  def writeWith[A](p: Path, f: OutputStream => ResultT[IO, A]): Hdfs[A] = for {
    _ <- mustexist(p) ||| mkdir(p.getParent)
    a <- filesystem.flatMap(fs =>
      Hdfs.fromResultTIO(ResultT.using(ResultT.safe[IO, OutputStream](fs.create(p))) { out =>
        f(out)
      }))
  } yield a

  def cp(src: FilePath, dest: FilePath, overwrite: Boolean): Hdfs[Unit] =
    cp(new Path(src.path), new Path(dest.path), overwrite)

  def cp(src: Path, dest: Path, overwrite: Boolean): Hdfs[Unit] = for {
    fs   <- filesystem
    conf <- configuration
    res  <- Hdfs.value(FileUtil.copy(fs, src, fs, dest, false, overwrite, conf))
    _    <- if(!res && overwrite) fail(s"Could not copy $src to $dest") else ok(())
  } yield ()

  def mkdir(p: FilePath): Hdfs[Boolean] =
    mkdir(new Path(p.path))

  def mkdir(p: Path): Hdfs[Boolean] =
    filesystem.map(fs => fs.mkdirs(p))

  def delete(p: FilePath): Hdfs[Unit] =
    delete(new Path(p.path))

  def delete(p: Path): Hdfs[Unit] =
    filesystem.map(fs => fs.delete(p, false))

  def deleteAll(p: FilePath): Hdfs[Unit] =
    deleteAll(new Path(p.path))

  def deleteAll(p: Path): Hdfs[Unit] =
    filesystem.map(fs => fs.delete(p, true))

  implicit def HdfsMonad: Monad[Hdfs] = new Monad[Hdfs] {
    def point[A](v: => A) = ok(v)
    def bind[A, B](m: Hdfs[A])(f: A => Hdfs[B]) = m.flatMap(f)
  }
}