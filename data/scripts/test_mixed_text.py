#!/usr/bin/env python3
"""Tests of mixed_text.py. Run: python3 -m unittest discover -s data/scripts -p 'test_*.py'"""
import unittest

import mixed_text as mt


def lexicon(ru, sr):
    lex = mt.Lexicon()
    lex.add("ru", ru)
    lex.add("sr", sr)
    return lex


class Rules(unittest.TestCase):
    def setUp(self):
        self.m = mt.Marker(lexicon("стол стол как как на и у", "сто сто код код на и у руски руски а а"), stress="ru")

    def test_letters_of_one_language_tell_it(self):
        self.assertEqual(self.m.mark("руски жизнь је живот", "sr"), "руски {жизнь} је живот")
        self.assertEqual(self.m.mark("кисель и кисељ", "ru"), "кисель и {кисељ}")

    def test_a_stress_mark_tells_russian_where_serbian_has_none(self):
        self.assertEqual(self.m.mark("руски живо́т је стомак", "sr"), "руски {живо́т} је стомак")
        self.assertEqual(mt.Marker(mt.Lexicon()).mark("живо́т", "sr"), "живо́т")

    def test_empty_braces_keep_a_text_unmarked(self):
        self.assertEqual(self.m.mark("стол је сто{}", "sr"), "стол је сто")
        self.assertEqual(self.m.mark("{стол} је сто{}", "sr"), "{стол} је сто")

    def test_a_word_written_in_one_language_only_is_of_it(self):
        self.assertEqual(self.m.mark("стол је сто, код као", "sr"), "{стол} је сто, код као")

    def test_a_word_of_both_takes_the_language_of_its_clause(self):
        self.assertEqual(self.m.mark("{x}", "sr"), "{x}", "marked by hand")
        self.assertEqual(self.m.mark("Имати: у меня есть и у меня нет", "sr"), "Имати: {у меня есть и у меня нет}")
        self.assertEqual(self.m.mark("на мой взгляд = по мом мишљењу", "sr"), "{на мой взгляд} = по мом мишљењу")
        self.assertEqual(self.m.mark("ићи на кино, на завод", "sr"), "ићи на кино, на завод")
        self.assertEqual(self.m.mark("код мене на жизнь", "sr"), "код мене на {жизнь}", "between the two: base")

    def test_the_text_names_the_language_of_a_word(self):
        self.assertEqual(self.m.mark("Српско стол је престо, а руски стол је сто", "sr"),
                         "Српско стол је престо, а руски {стол} је сто")
        self.assertEqual(self.m.mark("на руском као", "sr"), "на руском као")

    def test_the_words_of_a_comparison_are_its_only_russian(self):
        self.assertEqual(self.m.mark("живот или жизнь?", "sr", other_words={"живот", "жизнь"}), "{живот} или {жизнь}?")

    def test_labels_of_forms_are_in_the_other_language(self):
        self.assertEqual(self.m.mark("ген. взгля́да; мн. те; кратко: нов", "ru", labels=True, lexical=False),
                         "{ген}. взгля́да; {мн}. те; {кратко}: нов")
        sr = mt.Marker(mt.Lexicon())
        self.assertEqual(sr.mark("мн. паре (деньги), дрва", "sr", labels=True, lexical=False),
                         "{мн}. паре ({деньги}), дрва")
        self.assertEqual(sr.mark("дословно: как идёт", "sr", labels=True, lexical=False), "{дословно}: {как идёт}")
        self.assertEqual(sr.mark("не склоняется", "sr", labels=True, lexical=False), "{не склоняется}")


class Decks(unittest.TestCase):
    """The texts the bug was seen in, with the words of both decks."""

    @classmethod
    def setUpClass(cls):
        cls.m = mt.Marker(mt.Lexicon.load(), stress="ru")

    def test_serbian_notes_on_russian(self):
        for text, want in [
            ("на мой взгляд = по мом мишљењу", "{на мой взгляд} = по мом мишљењу"),
            ("Имати: у меня́ есть и у меня́ нет", "Имати: {у меня́ есть и у меня́ нет}"),
            ("Постати, бити, радити као: твори́тельный", "Постати, бити, радити као: {твори́тельный}"),
            ("Что́бы тамо где српски има „да“", "{Что́бы} тамо где српски има „да“"),
            ("{по отношению к} + дат. = према", "{по отношению к} + дат. = према"),
            ("Лажни пријатељ: руски живо́т је стомак, а српском животу одговара жизнь.",
             "Лажни пријатељ: руски {живо́т} је стомак, а српском животу одговара {жизнь}."),
        ]:
            self.assertEqual(self.m.mark(text, "sr"), want)


if __name__ == "__main__":
    unittest.main()
