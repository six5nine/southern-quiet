package test.broadcasting;

import me.insidezhou.southernquiet.event.CustomApplicationEvent;

import java.util.UUID;

@CustomApplicationEvent
public class NonSerializableEvent {
    private UUID id = UUID.randomUUID();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
