package com.twitter.app

// For testing fun
object SolarSystemPlanets {

  sealed abstract class Planet(
    val orderFromSun: Int,
    val name: String,
    val mass: Kilogram,
    val radius: Meter)
      extends Ordered[Planet] {

    def compare(that: Planet): Int =
      this.orderFromSun - that.orderFromSun

    lazy val surfaceGravity: Double =
      G * mass / (radius * radius)

    def surfaceWeight(otherMass: Kilogram): Double =
      otherMass * surfaceGravity

    override def toString =
      s"Name: $name, order: $orderFromSun, mass: $mass, radius: $radius, gravity: $surfaceGravity"
  }

  case object Mercury extends Planet(1, "Mercury", 3.303e+23, 2.4397e6)
  case object Venus extends Planet(2, "Venus", 4.869e+24, 6.0518e6)
  case object Earth extends Planet(3, "Earth", 5.976e+24, 6.3781e6)
  case object Mars extends Planet(4, "Mars", 6.421e+23, 3.3972e6)
  case object Jupiter extends Planet(5, "Jupiter", 1.9e+27, 7.1492e7)
  case object Saturn extends Planet(6, "Saturn", 5.688e+26, 6.0268e7)
  case object Uranus extends Planet(7, "Uranus", 8.686e+25, 2.5559e7)
  case object Neptune extends Planet(8, "Neptune", 1.024e+26, 2.4746e7)

  val planets: Set[Planet] = Set(
    Mercury,
    Venus,
    Earth,
    Mars,
    Jupiter,
    Saturn,
    Uranus,
    Neptune
  )

  def parsePlanet(name: String): Planet =
    planets
      .find(_.name == name)
      .getOrElse(throw new IllegalArgumentException(s"No planet named: name"))

  type Kilogram = Double
  type Meter = Double
  private val G = 6.67300E-11 // universal gravitational constant (m3 kg-1 s-2)
}

class ColonizeTestApp extends App {
  private[this] var planetToColonize: SolarSystemPlanets.Planet = SolarSystemPlanets.Earth

  override protected val failfastOnFlagsNotParsed: Boolean = true

  override protected def exitOnError(reason: String, details: => String): Unit = {
    throw new Exception(reason)
  }

  private[this] val pfm: Flaggable[SolarSystemPlanets.Planet] =
    Flaggable.mandatory[SolarSystemPlanets.Planet](SolarSystemPlanets.parsePlanet)
  private[this] val planetF: Flag[SolarSystemPlanets.Planet] =
    flag[SolarSystemPlanets.Planet]("planet", planetToColonize, "Planet to Colonize")(pfm)

  def main(): Unit = {
    planetToColonize = planetF()
  }

  def colony: SolarSystemPlanets.Planet = planetToColonize
}
