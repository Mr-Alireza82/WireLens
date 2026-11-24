package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.net.IpAddress;
import io.github.mralireza82.wirelens.domain.net.Port;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Optional;

/**
 * An IP address and port being typed, validated on every keystroke with the domain's own rules. The view shows
 * {@link #addressError()} and {@link #portError()} next to the fields and disables actions while invalid, so an
 * invalid endpoint can never be submitted.
 */
public final class EndpointInput {
    private final StringProperty address = new SimpleStringProperty("");
    private final StringProperty port = new SimpleStringProperty("");
    private final StringBinding addressError =
            Bindings.createStringBinding(() -> IpAddress.validate(address.get()).orElse(""), address);
    private final StringBinding portError =
            Bindings.createStringBinding(() -> Port.validate(port.get()).orElse(""), port);
    private final BooleanBinding valid = addressError.isEmpty().and(portError.isEmpty());

    public EndpointInput(String initialAddress, String initialPort) {
        address.set(initialAddress);
        port.set(initialPort);
    }

    public StringProperty address() {
        return address;
    }

    public StringProperty port() {
        return port;
    }

    /** Empty when the address is valid, otherwise a message for the user. */
    public StringBinding addressError() {
        return addressError;
    }

    public StringBinding portError() {
        return portError;
    }

    public BooleanBinding valid() {
        return valid;
    }

    /** The endpoint if both fields are valid. */
    public Optional<Endpoint> endpoint() {
        if (!valid.get()) {
            return Optional.empty();
        }
        return Optional.of(new Endpoint(IpAddress.parse(address.get()), Port.parse(port.get())));
    }

    /** Shows an endpoint in the fields, e.g. after loading settings. */
    public void set(Endpoint endpoint) {
        address.set(endpoint.address().toString());
        port.set(endpoint.port().toString());
    }
}
