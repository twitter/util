scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-validation-internal-hibernate-properties-javabeans",
        repo = artifactory,
    ),
    strict_deps = True,
    dependencies = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "3rdparty/jvm/org/hibernate/validator:hibernate-validator",
        "util/util-reflect/src/main/scala/com/twitter/util/reflect",
    ],
    exports = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "3rdparty/jvm/org/hibernate/validator:hibernate-validator",
    ],
)
