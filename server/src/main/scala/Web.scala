package com.herokuapp.xtend

import scalaz._, syntax.std.tuple._, syntax.either._
import unfiltered.request._
import unfiltered.response._
import util.Properties
import sbt.{Path=>_,Logger=>_,Level=>_,_}
import java.io.{Writer,File}
import org.eclipse.xtend.core.compiler.batch.XtendBatchCompiler
import org.apache.log4j.BasicConfigurator
import org.eclipse.xtend.core.XtendStandaloneSetup
import org.json4s._
import org.json4s.native.JsonMethods.{parseOpt, pretty, render}
import org.apache.log4j.{Logger => Log4jLogger,Level,WriterAppender,HTMLLayout,SimpleLayout,Layout}

class App(debug: Boolean) extends unfiltered.filter.Plan {

  lazy val jarList = { file("lib_managed") ** "*.jar" get } :+ IO.classLocationFile[Predef.type]

  def xtend2java(src: Seq[SourceFile]) = {
    IO.withTemporaryDirectory{in =>
      src.foreach{f =>
        IO.writeLines(in / f.name, Seq(f.contents) )
      }
      IO.withTemporaryDirectory{out =>
        compileXtend(out,in,jarList)
      }
    }
  }

  def createLoggers():(Writer, Writer) = {
    val logger = Log4jLogger.getLogger("org.eclipse.xtext")
    logger.setAdditivity(false)
    logger.setLevel(Level.DEBUG)
    logger.removeAllAppenders()
    def add(layout:Layout):Writer = {
      val w = new java.io.CharArrayWriter
      val appender = new WriterAppender(layout,w)
      logger.addAppender(appender)
      w
    }
    (new SimpleLayout(),new HTMLLayout()).mapElements(add,add)
  }

  def compileXtend(out: File, in: File, cp: Seq[File]): ErrorMessage \/ Seq[SourceFile] = {
    val (simple,html) = createLoggers()
    try{
      val resultWriter = new java.io.CharArrayWriter
      val injector = new XtendStandaloneSetup().createInjectorAndDoEMFRegistration
      val c = injector.getInstance(classOf[XtendBatchCompiler])
      c.setOutputPath(out.toString())
      c.setSourcePath(in.toString())
      c.setOutputWriter(resultWriter)
//      c.setErrorWriter(simple) // TODO invalid ?
      c.setVerbose(true)
      c.setClassPath(cp.map{_.getAbsolutePath}.mkString(File.pathSeparator))
      if(c.compile()){
        (out ** "*.java").get.map{f => SourceFile(f.getName,IO.read(f))}.right
      }else{
        ErrorMessage(("compile fail" + simple.toString),html.toString).left
      }
    }catch{
      case e: Throwable =>
        ErrorMessage(Seq(simple.toString,e.toString).mkString("\n\n"),html.toString).left
    }
  }

  val GITHUB = "https://github.com/xuwei-k/xtend.herokuapp.com"
  val XTEND_SITE = "http://www.eclipse.org/xtend/"

  def intent = {
    case r @ POST(Path("/")) =>
      val str = Body.string(r)
      if(debug){
        println(str)
        parseOpt(str).map{ j =>
          println(pretty(render(j)))
        }.getOrElse{
          println("fail parse json. " + str + " is not valid json")
        }
      }

      val sourceFiles = for{
        JObject(List(JField(Common.FILES,JObject(json)))) <- parseOpt(str)
        files = for{
          JField(name,JString(contents)) <- json
        } yield SourceFile(name,contents)
      }yield files

      sourceFiles.map(xtend2java).map{_.fold(
        error => Result(true, error, Nil),
        seq => Result(false, EmptyError, seq)
      )}.getOrElse{
        val msg = "invalid params " + str
        Result(true,ErrorMessage(msg,msg),Nil)
      }.toJsonResponse

    case GET(Path("/")) =>

      Html(
      <html>
        <head>
          <script type="text/javascript" src="http://code.jquery.com/jquery-2.0.3.min.js"></script>
          <script type="text/javascript" src="/xtendheroku.js"></script>
          <title>xtend {XtendVersion()} web interface</title>
          <link rel="stylesheet" href="./xtendheroku.css" type="text/css" />
          <script src="//cdnjs.cloudflare.com/ajax/libs/prettify/r298/prettify.js" type="text/javascript"></script>
          <link href="//cdnjs.cloudflare.com/ajax/libs/prettify/r298/prettify.css" rel="stylesheet" type="text/css"/>
        </head>
        <body>
          <h1><a href={XTEND_SITE}>xtend</a> {XtendVersion()} web interface</h1>
          <p><a href={GITHUB}>this program source code</a></p>
          <p>
            <button id='compile' >compile</button>
            <button id='clear_javacode' >clear java code</button>
            <button id='clear_error_message' >clear error message</button>
            <form>
            <input type='radio' name='xtend_edit_type' id='edit_type_auto' value='auto'>auto</input>
            <input type='radio' name='xtend_edit_type' id='edit_type_manual' value='manual'>manual</input>
            </form>
          </p>
          <div class='src_wrap_div'>
            <div>
              <div id="xtendcode_wrap" class="source_code">
                <p class="xtend_class_wrap">class <input id="xtend_class_name" type="text" />{"{"}</p>
                <p id='xtend_file_name_wrap'>file name<input id="xtend_file_name" type="text" /></p>
                <textarea id='xtendcode'></textarea>
                <p class="xtend_class_wrap" >{"}"}</p>
              </div>
            </div>
            <div><pre class="source_code prettyprint" id='javacode'/></div>
          </div>
          <div id='error_message' />
        </body>
      </html>
      )
  }
}

case class SourceFile(name: String, contents: String)

case class ErrorMessage(simple: String, html: String)

object EmptyError extends ErrorMessage("", "")

case class Result(error: Boolean, msg: ErrorMessage, result: Seq[SourceFile]){
  import org.json4s.JsonDSL._

  def toJsonResponse = Json(
    (Common.ERROR   -> error) ~
    (Common.MESSAGE -> msg.simple) ~
    (Common.HTML_MESSAGE -> msg.html) ~
    (Common.RESULT  -> JObject(result.map{f => JField(f.name,JString(f.contents))}.toList) )
  )
}

object Web {
  def main(args: Array[String]) {
    val debug = Option(args).flatMap{_.headOption.map{java.lang.Boolean.parseBoolean}}.getOrElse(false)
    val port = Properties.envOrElse("PORT",Common.DEFAULT_PORT).toInt
    println("debug mode=" + debug + " port=" + port)
    if(debug){
      unfiltered.util.Browser.open("http://127.0.0.1:" + port)
    }
    unfiltered.jetty.Http(port).resources(getClass.getResource("/")).filter(new App(debug)).run
  }
}

