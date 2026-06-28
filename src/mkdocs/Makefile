# Build the Tentackle documentation website (MkDocs + Material).
#
# The docs/ tree is generated from the repository's real docs and README by
# prepare.py; every target re-stages first so the site always reflects the
# current sources.

PYTHON ?= python3

.PHONY: help install prepare serve build strict clean

help:
	@echo "Targets:"
	@echo "  install  - pip install the build dependencies (requirements.txt)"
	@echo "  prepare  - stage the repo docs + README into ./docs"
	@echo "  serve    - prepare, then live-preview at http://127.0.0.1:8000"
	@echo "  build    - prepare, then build into ./site (strict: fails on warnings)"
	@echo "  clean    - remove generated docs/, site/ and caches"

install:
	$(PYTHON) -m pip install -r requirements.txt

prepare:
	$(PYTHON) prepare.py

serve: prepare
	mkdocs serve

build: prepare
	mkdocs build --strict

# Alias kept for clarity; `build` is already strict.
strict: build

clean:
	rm -rf docs site .cache
