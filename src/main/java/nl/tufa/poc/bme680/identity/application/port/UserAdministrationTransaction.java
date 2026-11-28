package nl.tufa.poc.bme680.identity.application.port;

import java.util.function.Supplier;

/**
 * Executes a complete administration use case atomically and exclusively with
 * respect to other user mutations, including bootstrap on an empty store.
 */
public interface UserAdministrationTransaction {
	<T> T execute(Supplier<T> work);
}
