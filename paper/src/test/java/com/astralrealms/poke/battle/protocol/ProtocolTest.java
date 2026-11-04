package com.astralrealms.poke.battle.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProtocolTest {

    @Test
    void parsesArgumentsAndTags() {
        ProtocolLine line = ProtocolLine.parse("|-damage|p1a: 2|17/40 par|[from] item: Rocky Helmet|[of] p2a: 1");

        assertThat(line).isNotNull();
        assertThat(line.type()).isEqualTo("-damage");
        assertThat(line.args()).containsExactly("p1a: 2", "17/40 par");
        assertThat(line.tag("from")).isEqualTo("item: Rocky Helmet");
        assertThat(ProtocolLine.effect(line.tag("from"))).isEqualTo(new ProtocolLine.Effect("item", "rockyhelmet"));
        assertThat(line.tag("of")).isEqualTo("p2a: 1");
        assertThat(ProtocolLine.parse("|")).isNull();
        assertThat(ProtocolLine.parse("plain text")).isNull();
        assertThat(ProtocolLine.parse("|-crit|p2a: 1").has("silent")).isFalse();
        assertThat(ProtocolLine.parse("|-curestatus|p1a: 1|slp|[msg]").has("msg")).isTrue();
    }

    @Test
    void parsesReferencesAndConditions() {
        assertThat(PokemonRef.parse("p2a: 1")).isEqualTo(new PokemonRef("p2", "a", "1"));
        assertThat(PokemonRef.parse("p1: 3")).isEqualTo(new PokemonRef("p1", "", "3"));
        assertThat(PokemonRef.parse("nothing")).isNull();

        assertThat(Condition.parse("35/120 par")).isEqualTo(new Condition(35, 120, "par"));
        assertThat(Condition.parse("0 fnt").fainted()).isTrue();
        assertThat(Condition.parse("1/400").percent()).isEqualTo(1);
        assertThat(Condition.parse("54/100").percent()).isEqualTo(54);
        assertThat(Condition.parse("")).isNull();
    }

    @Test
    void readsARequest() {
        String json = """
                {"active":[{"moves":[
                  {"move":"Spark Jab","id":"sparkjab","pp":0,"maxpp":24,"target":"normal","disabled":true},
                  {"move":"Leaf Cut","id":"leafcut","pp":12,"maxpp":16,"target":"normal","disabled":false},
                  {"move":"Glare Down","id":"glaredown","pp":5,"maxpp":5,"target":"normal","disabled":"Taunt"}]}],
                 "side":{"name":"Elise","id":"p1","pokemon":[
                  {"ident":"p1: 2","details":"Leafkit, L12, F","condition":"30/38","active":true},
                  {"ident":"p1: 1","details":"Pebblit, L9","condition":"0 fnt","active":false},
                  {"ident":"p1: 3","details":"Emberling, L10, M","condition":"21/31 brn","active":false}]},
                 "rqid":7}""";
        ChoiceRequest request = ChoiceRequest.parse(json);

        assertThat(request.rqid()).isEqualTo(7);
        assertThat(request.choosable()).isTrue();
        assertThat(request.moves()).extracting(ChoiceRequest.MoveOption::disabled).containsExactly(true, false, true);
        assertThat(request.canUseMove(1)).isFalse();
        assertThat(request.canUseMove(2)).isTrue();
        assertThat(request.canUseMove(4)).isFalse();
        assertThat(request.active().orElseThrow().label()).isEqualTo("2");
        // switch N names the current position, not the start order
        assertThat(request.canSwitchTo(1)).isFalse();
        assertThat(request.canSwitchTo(2)).isFalse();
        assertThat(request.canSwitchTo(3)).isTrue();
    }

    @Test
    void readsForcedSwitchesAndWaits() {
        ChoiceRequest forced = ChoiceRequest.parse("""
                {"forceSwitch":[true],"side":{"pokemon":[
                  {"ident":"p1: 1","condition":"0 fnt","active":true},
                  {"ident":"p1: 2","condition":"12/20","active":false}]},"rqid":3}""");
        assertThat(forced.forceSwitch()).isTrue();
        assertThat(forced.moves()).isEmpty();
        assertThat(forced.canUseMove(1)).isFalse();
        assertThat(forced.canSwitchTo(2)).isTrue();

        assertThat(ChoiceRequest.parse("{\"wait\":true,\"side\":{\"pokemon\":[]},\"rqid\":4}").choosable()).isFalse();
    }
}
