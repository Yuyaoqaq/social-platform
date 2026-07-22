package com.cy.share.rpc.serialize;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonSerializerTest {

    record Person(String name, int age) {}

    @Test
    void roundTripSimpleObject() throws Exception {
        JsonSerializer ser = new JsonSerializer();
        Person original = new Person("Alice", 30);
        byte[] bytes = ser.serialize(original);
        Person restored = ser.deserialize(bytes, Person.class);
        assertEquals("Alice", restored.name());
        assertEquals(30, restored.age());
    }

    @Test
    void roundTripString() throws Exception {
        JsonSerializer ser = new JsonSerializer();
        byte[] bytes = ser.serialize("hello");
        String restored = ser.deserialize(bytes, String.class);
        assertEquals("hello", restored);
    }

    @Test
    void getTypeReturnsJsonCode() {
        assertEquals((byte) 0x01, new JsonSerializer().getType());
    }
}
