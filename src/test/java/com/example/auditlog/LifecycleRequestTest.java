package com.example.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.example.auditlog.audit.InvalidEventException;
import com.example.auditlog.audit.StoredJson;
import com.example.auditlog.lifecycle.LifecycleRequest;
import org.junit.jupiter.api.Test;

class LifecycleRequestTest {

    private final LifecycleRequest parser = new LifecycleRequest(new StoredJson());

    @Test
    void retentionRequiresExactFieldsReasonAndValidOperationIdentity() {
        UUID id = UUID.randomUUID();
        assertThat(parser.retention(bytes("{\"operationId\":\"" + id + "\",\"reason\":\"policy\"}")).operationId()).isEqualTo(id);
        for (String body : new String[] {"{}", "{\"operationId\":\"bad\",\"reason\":\"policy\"}",
                "{\"operationId\":\"" + id + "\",\"reason\":\" \",\"actorId\":\"spoofed\"}",
                "{\"operationId\":\"" + id + "\",\"reason\":\"a\",\"reason\":\"duplicate\"}"}) {
            assertThatThrownBy(() -> parser.retention(bytes(body))).isInstanceOf(InvalidEventException.class);
        }
    }

    @Test
    void erasureRequiresExplicitConfirmationApprovalAndUniqueBoundedPointers() {
        String valid = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"reason\":\"privacy\","
                + "\"approvalReference\":\"synthetic-approved\",\"confirmation\":\"ERASE_FIELD_KEYS\",\"paths\":[\"/b\",\"/a\"]}";
        assertThat(parser.redaction(bytes(valid)).paths()).containsExactly("/a", "/b");
        for (String invalid : new String[] {valid.replace("ERASE_FIELD_KEYS", "false"), valid.replace("synthetic-approved", " "),
                valid.replace("[\"/b\",\"/a\"]", "[]"), valid.replace("[\"/b\",\"/a\"]", "[\"/a\",\"/a\"]"),
                valid.replace("/a", "not-a-pointer"), valid.replace("/a", "/bad~2"), valid + " {}"}) {
            assertThatThrownBy(() -> parser.redaction(bytes(invalid))).isInstanceOf(InvalidEventException.class);
        }
    }

    @Test
    void oversizedLifecycleRequestsAreRejectedBeforeParsing() {
        assertThatThrownBy(() -> parser.retention(new byte[16 * 1024 + 1]))
                .isInstanceOfSatisfying(InvalidEventException.class, error -> assertThat(error.status()).isEqualTo(413));
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
