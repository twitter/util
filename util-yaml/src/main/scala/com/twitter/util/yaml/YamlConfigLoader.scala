package com.twitter.util.yaml

import java.io.{BufferedReader, File, FileInputStream, InputStream, InputStreamReader, Reader}
import java.util.{Map => JMap, List => JList}
import com.twitter.logging.Logger
import org.yaml.snakeyaml.Yaml

object YamlConfigLoader {
  /** System variable that can be set to config directory */
  val YamlConfigRoot = "YAML_CONFIG_ROOT"

  /** Directory prefix when searching in resources */
  val ResourceDir = "/config/" // leading "/" needed so getResourceAsStream looks in class path

  /** Directory searched for yaml files when YAML_CONFIG_ROOT not set */
  var defaultConfigRoot = ""

  private def configRoot: String = System.getenv(YamlConfigRoot) match {
    case null => normalizeDir(defaultConfigRoot)
    case dir => normalizeDir(dir)
  }

  private val log = Logger("YamlConfig")

  /**
   * Normalize a directory name so it has an ending "/" unless it is empty
   */
  def normalizeDir(dir: String) =
    dir + (if (!dir.isEmpty && !dir.endsWith("/")) "/" else "")
}

abstract class YamlConfigLoader[T : Manifest](emptyConfig: T) {
  import YamlConfigLoader._

  private val yaml = new Yaml
  private val erasure = manifest[T].erasure

  // Cast object to erased lost type and return emptyConfig if object is null
  private def erasedCastOrEmpty(obj: Any): T = obj match {
    case null => emptyConfig
    case _ => erasure.cast(obj).asInstanceOf[T]
  }

  // helpers for loading Yaml Map
  protected[util] def loadYaml(io: InputStream): T = erasedCastOrEmpty(yaml.load(io))
  protected[util] def loadYaml(io: Reader): T = erasedCastOrEmpty(yaml.load(io))

  /**
   * Try loading yamlOrPath as yaml with type T, otherwise load yamlOrPath as a file.
   *
   * To load a file, it first the root directory "YAML_CONFIG_ROOT", which is a system variable
   * that defaults to YamlConfigLoader.defaultConfigRoot). It then looks in resources directory.
   */
  protected[util] def loadYaml(yamlOrPath: String): T = {
    try {
      erasedCastOrEmpty(yaml.load(yamlOrPath))
    } catch { case e: ClassCastException =>
      val path = configRoot + yamlOrPath
      val file = new File(path)

      if (file.exists()) {
        val in = new InputStreamReader(new FileInputStream(file), "UTF-8")
        val reader = new BufferedReader(in, 4096)
        try {
          log.info("Loading yaml file from path: " + path)
          erasedCastOrEmpty((new Yaml).load(reader))
        } finally {
          reader.close()
        }
      } else if (yamlOrPath.endsWith(".yml")) {
        // Assumes yaml files in resource dir have extension .yml
        val resPath =  ResourceDir + yamlOrPath
        log.info("Loading yaml file from resources: " + resPath)
        getClass.getResourceAsStream(resPath) match {
          case null =>
            throw new IllegalArgumentException("File not found: " + resPath)
          case io => loadYaml(io)
        }
      } else {
        throw e
      }
    }
  }
}
