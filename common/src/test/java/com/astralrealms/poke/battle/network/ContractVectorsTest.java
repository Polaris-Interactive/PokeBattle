package com.astralrealms.poke.battle.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.astralrealms.core.packet.Packet;
import com.astralrealms.core.packet.io.PacketDeserializer;
import com.astralrealms.core.packet.io.PacketSerializer;
import com.astralrealms.poke.battle.network.packet.BattleAttachPacket;
import com.astralrealms.poke.battle.network.packet.BattleRecoverPacket;
import com.astralrealms.poke.battle.network.packet.BattleChoiceErrorPacket;
import com.astralrealms.poke.battle.network.packet.BattleChoicePacket;
import com.astralrealms.poke.battle.network.packet.BattleEndPacket;
import com.astralrealms.poke.battle.network.packet.BattleEndedPacket;
import com.astralrealms.poke.battle.network.packet.BattleMemberState;
import com.astralrealms.poke.battle.network.packet.BattleRequestPacket;
import com.astralrealms.poke.battle.network.packet.BattleSide;
import com.astralrealms.poke.battle.network.packet.BattleStartPacket;
import com.astralrealms.poke.battle.network.packet.BattleStartReplyPacket;
import com.astralrealms.poke.battle.network.packet.BattleTeamState;
import com.astralrealms.poke.battle.network.packet.BattleUpdatePacket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The byte vectors of the battle protocol ({@code contract/vectors.json}), shared with {@code poke-battle-service}.
 * AstralCore's serializer is the reference: every vector's {@code hex} must be what it writes, and must read back to
 * the same packet. The Node codec runs the same file.
 * <p>
 * After adding a vector or changing a packet on purpose, regenerate the bytes with
 * {@code mvn test -pl common -Dcontract.regenerate=true}, then copy the file to the worker
 * ({@code npm run sync-vectors}).
 */
class ContractVectorsTest {

    private static final String RESOURCE = "/contract/vectors.json";
    private static final Path SOURCE = Path.of("src/test/resources/contract/vectors.json");

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final PokeBattlePacketRegistry registry = new PokeBattlePacketRegistry();
    private final PacketSerializer serializer = new PacketSerializer(this.registry);
    private final PacketDeserializer deserializer = new PacketDeserializer(this.registry);

    @Test
    void everyVectorMatchesAstralCoreEncoding() throws IOException {
        JsonNode vectors;
        try (InputStream input = ContractVectorsTest.class.getResourceAsStream(RESOURCE)) {
            vectors = this.mapper.readTree(input);
        }
        boolean regenerate = Boolean.getBoolean("contract.regenerate");

        for (JsonNode vector : vectors) {
            Packet packet = this.build(vector.get("packet").asText(), vector.get("fields"));
            byte[] bytes = this.serializer.serialize(packet);
            String hex = HexFormat.of().formatHex(bytes);

            if (regenerate) {
                ((ObjectNode) vector).put("id", this.registry.getPacketId(packet.getClass()).orElseThrow());
                ((ObjectNode) vector).put("hex", hex);
                continue;
            }

            assertThat(vector.get("id").asInt())
                    .as(vector.get("packet").asText() + " id")
                    .isEqualTo(this.registry.getPacketId(packet.getClass()).orElseThrow());
            assertThat(hex).as(vector.get("packet").asText() + " bytes").isEqualTo(vector.get("hex").asText());

            Packet read = this.deserializer.deserialize(HexFormat.of().parseHex(hex));
            assertThat(read).isExactlyInstanceOf(packet.getClass());
            assertThat(HexFormat.of().formatHex(this.serializer.serialize(read))).isEqualTo(hex);
        }

        if (regenerate)
            Files.writeString(SOURCE, this.mapper.writeValueAsString(vectors) + "\n");
    }

    private Packet build(String name, JsonNode fields) {
        UUID battleId = UUID.fromString(fields.get("battleId").asText());
        return switch (name) {
            case "BattleStartPacket" -> new BattleStartPacket(battleId, fields.get("shardId").asText(),
                    fields.get("format").asText(), fields.get("seed").asText(), this.sides(fields.get("sides")),
                    fields.get("turnTimerSeconds").asInt(), fields.get("allowBag").asBoolean(),
                    fields.get("allowCapture").asBoolean());
            case "BattleStartReplyPacket" -> new BattleStartReplyPacket(battleId, fields.get("accepted").asBoolean(),
                    fields.get("workerId").asText(), fields.get("reason").asText());
            case "BattleChoicePacket" -> new BattleChoicePacket(battleId, fields.get("side").asText(),
                    fields.get("rqid").asInt(), fields.get("choice").asText());
            case "BattleEndPacket" -> new BattleEndPacket(battleId, fields.get("result").asText(),
                    fields.get("reason").asText());
            case "BattleAttachPacket" -> new BattleAttachPacket(battleId, fields.get("shardId").asText());
            case "BattleRecoverPacket" -> new BattleRecoverPacket(battleId, fields.get("shardId").asText());
            case "BattleUpdatePacket" -> new BattleUpdatePacket(battleId, fields.get("seq").asLong(),
                    fields.get("side").asText(), this.strings(fields.get("lines")));
            case "BattleRequestPacket" -> new BattleRequestPacket(battleId, fields.get("seq").asLong(),
                    fields.get("side").asText(), fields.get("rqid").asInt(), fields.get("request").asText());
            case "BattleChoiceErrorPacket" -> new BattleChoiceErrorPacket(battleId, fields.get("seq").asLong(),
                    fields.get("side").asText(), fields.get("rqid").asInt(), fields.get("message").asText());
            case "BattleEndedPacket" -> new BattleEndedPacket(battleId, fields.get("seq").asLong(),
                    fields.get("result").asText(), fields.get("reason").asText(), fields.get("turns").asInt(),
                    this.teams(fields.get("teams")));
            default -> throw new IllegalArgumentException("Unknown packet " + name);
        };
    }

    private List<BattleSide> sides(JsonNode nodes) {
        List<BattleSide> sides = new ArrayList<>();
        for (JsonNode side : nodes)
            sides.add(new BattleSide(side.get("slot").asText(), side.get("name").asText(),
                    side.get("playerId").isNull() ? null : UUID.fromString(side.get("playerId").asText()),
                    side.get("team").asText(), side.get("ai").asText(), this.members(side.get("state"))));
        return sides;
    }

    private List<BattleTeamState> teams(JsonNode nodes) {
        List<BattleTeamState> teams = new ArrayList<>();
        for (JsonNode team : nodes)
            teams.add(new BattleTeamState(team.get("slot").asText(), this.members(team.get("members"))));
        return teams;
    }

    private List<BattleMemberState> members(JsonNode nodes) {
        List<BattleMemberState> members = new ArrayList<>();
        for (JsonNode member : nodes)
            members.add(new BattleMemberState(member.get("hp").asInt(), member.get("status").asText(),
                    this.ints(member.get("pp")), this.ints(member.get("maxPp"))));
        return members;
    }

    private List<Integer> ints(JsonNode nodes) {
        List<Integer> ints = new ArrayList<>();
        nodes.forEach(node -> ints.add(node.asInt()));
        return ints;
    }

    private List<String> strings(JsonNode nodes) {
        List<String> strings = new ArrayList<>();
        nodes.forEach(node -> strings.add(node.asText()));
        return strings;
    }
}
