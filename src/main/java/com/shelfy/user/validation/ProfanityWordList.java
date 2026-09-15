package com.shelfy.user.validation;

import java.util.Set;

final class ProfanityWordList {

    static final Set<String> WORDS = Set.of(
            "puta", "puto", "gilipollas", "cabron", "cabrona", "coño", "mierda", "joder",
            "polla", "pollas", "follar", "zorra", "maricon", "marica", "capullo", "subnormal",
            "retrasado", "hijoputa", "hdp", "pendejo", "verga", "chinga", "concha",

            "collons", "cony", "merda", "cabro", "fillputa",

            "fuck", "shit", "bitch", "asshole", "dick", "pussy", "cunt", "bastard", "slut",
            "whore", "nigger", "faggot", "retard", "motherfucker"
    );

    private ProfanityWordList() {
    }
}
