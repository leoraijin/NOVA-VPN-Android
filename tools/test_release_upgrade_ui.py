import pathlib
import unittest
import xml.etree.ElementTree as ET
from release_upgrade_ui import find_label, profile_disclosure, tab_selected


class ReleaseUpgradeSelectors(unittest.TestCase):
    def setUp(self):
        self.tree = ET.parse(pathlib.Path(__file__).parent / 'fixtures/upgrade-ios-collapsed.xml').getroot()

    def test_actual_captured_profile_is_collapsed_and_has_its_own_click_target(self):
        self.assertIs(profile_disclosure(self.tree, 'NOVA_UPGRADE_0_2_54'), False)
        self.assertEqual(find_label(self.tree, 'NOVA_UPGRADE_0_2_54'), (540, 1515))

    def test_navigation_label_resolves_to_its_clickable_button(self):
        self.assertEqual(find_label(self.tree, 'Главная'), (174, 1743))
        self.assertFalse(tab_selected(self.tree, 'Главная'))

    def test_selected_tab_is_confirmed_without_forcing_an_unsupported_click(self):
        self.assertIsNone(find_label(self.tree, 'Серверы'))
        self.assertTrue(tab_selected(self.tree, 'Серверы'))

    def test_profile_under_navigation_is_not_clickable_through_the_dock(self):
        parents = {child: parent for parent in self.tree.iter() for child in parent}
        title = next(node for node in self.tree.iter('node') if node.get('text') == 'NOVA_UPGRADE_0_2_54')
        parents[title].set('bounds', '[53,1690][1027,1790]')
        self.assertIsNone(find_label(self.tree, 'NOVA_UPGRADE_0_2_54'))