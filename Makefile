SHELL:=/usr/bin/env bash

.PHONY: default
default: | help

.PHONY: start-db
start-db: ## Start postgres
	docker-compose up -d --build postgresdb-subsets

.PHONY: stop-db
stop-db: ## Stop postgres
	docker-compose rm --force --stop postgresdb-subsets

.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-45s\033[0m %s\n", $$1, $$2}'
