.PHONY: test

develop:
	clj -A:dev -X dev/-main
	mix phx.server
