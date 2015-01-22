usage:
	@echo 'No default make target is provided.'
	@echo 'Run `make <filename>` to run the code generator on the given file,'
	@echo 'or edit the Makefile to change how the generated file is produced.'
	@false

../util-test/src/main/scala/com/twitter/util/testing/ArgumentCapture.scala: util-test/ArgumentCapture.scala.mako
	mako-render util-test/ArgumentCapture.scala.mako > $@

.PHONY: usage
