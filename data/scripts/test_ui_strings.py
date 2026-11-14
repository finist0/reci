import unittest

import ui_strings


class UiStringsTest(unittest.TestCase):
    def test_every_interface_string_is_translated(self):
        table = ui_strings.json.loads(ui_strings.TABLE.read_text(encoding="utf-8"))
        missing = [k for k in ui_strings.keys() if not all(table.get(k, {}).get(l) for l in ui_strings.LANGS)]
        self.assertEqual(missing, [])

    def test_translations_keep_the_placeholders(self):
        table = ui_strings.json.loads(ui_strings.TABLE.read_text(encoding="utf-8"))
        for k, tr in table.items():
            for l, v in tr.items():
                self.assertEqual(ui_strings.re.findall(r"%[sd]", k), ui_strings.re.findall(r"%[sd]", v), (k, l))


if __name__ == "__main__":
    unittest.main()
