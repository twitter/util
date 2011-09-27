package com.twitter.util.yaml

import java.io.{BufferedReader, File, FileInputStream, InputStream, InputStreamReader, Reader}
import java.util.{List => JList, Collections}
import org.yaml.snakeyaml.Yaml
import scala.collection.JavaConversions._

/** List Yaml cofiguration file (e.g. internal_applications.yml). */
class ListYamlConfig(yamlList: JList[String]) extends Iterable[String] {

  def this(io: InputStream) =
    this(ListYamlConfig.loadYaml(io))
  def this(io: Reader) =
    this(ListYamlConfig.loadYaml(io))
  def this(yamlOrPath: String) =
    this(ListYamlConfig.loadYaml(yamlOrPath))

  def apply(index: Int) = yamlList.get(index)
  def contains(value: String) = yamlList.contains(value)
  def iterator = yamlList.iterator()
}

object ListYamlConfig extends YamlConfigLoader[JList[String]](
  Collections.EMPTY_LIST.asInstanceOf[JList[String]]
)
