package com.twitter.util.routing

/**
 * Functional alias for creating a [[Router router]] given a label and
 * all of the defined routes for the router to be generated.
 */
abstract class Generator[Input, Route, +RouterType <: Router[Input, Route]]
    extends (RouterInfo[Route] => RouterType)
