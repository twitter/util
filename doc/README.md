# Util Docs

To generate the user guide website locally, `cd` into the `util`
directory and run the following commands.

    $ ./sbt util-doc/make-site
    $ open doc/target/site/index.html

To publish a new version of the util docs to GitHub Pages:

    1. Make sure SBT sees the current version as the release version of
       Util (and not the SNAPSHOT version) by running from the master branch.
    2. `cd` into the `util` directory.
    3. Execute the script `pushsite.bash`.
