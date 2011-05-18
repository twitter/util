# util-hashing

Util-hashing is a collection of hash functions and hashing distributors (eg. ketama).

This library is released under the Apache Software License, version 2, which
should be included with the source in a file named `LICENSE`.


## Building

Use sbt (simple-build-tool) to build:

    $ sbt clean update package-dist

The finished jar will be in `dist/`.


## Using

To use hash functions:

    KeyHasher.FNV1_32.hashKey("string".getBytes)

Available hash functions are:

    FNV1_32
    FNV1A_32
    FNV1_64
    FNV1A_64
    KETAMA
    CRC32_ITU
    HSIEH

To use ketama distributor:

    val nodes = List(KetamaNode("host:port", weight, client))
    val distributor = new KetamaDistributor(nodes, KeyHasher.FNV1_32)
    distributor.nodeForKey("abc") // => client