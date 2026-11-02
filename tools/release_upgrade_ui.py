"""Selectors for the real release-upgrade UI; never tap through the navigation dock."""
import re

NAV_LABELS = {'Главная', 'Серверы', 'Маршруты', 'Настройки'}
NAV_DESCRIPTIONS = {'Главная', 'Серверы', 'Маршрутизация', 'Настройки'}


def bounds(node):
    values = list(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))
    return values if len(values) == 4 else None


def find_label(tree, label):
    parents = {child: parent for parent in tree.iter() for child in parent}
    dock = [bounds(node) for node in tree.iter('node') if node.get('content-desc') in NAV_DESCRIPTIONS]
    dock = [box for box in dock if box]
    for node in tree.iter('node'):
        if node.get('text') != label or node.get('enabled') != 'true':
            continue
        while node is not None and node.get('clickable') != 'true':
            node = parents.get(node)
        if node is None:
            continue
        box = bounds(node)
        if box is None or box[2] <= box[0] or box[3] <= box[1]:
            continue
        x, y = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
        if x < 0 or y < 0:
            continue
        if label not in NAV_LABELS and any(left <= x < right and top <= y < bottom for left, top, right, bottom in dock):
            continue
        return x, y
    return None


def profile_disclosure(tree, name):
    parents = {child: parent for parent in tree.iter() for child in parent}
    for node in tree.iter('node'):
        if node.get('text') != name:
            continue
        parent = parents.get(node)
        while parent is not None:
            descriptions = {child.get('content-desc') for child in parent.iter('node')}
            if 'Свернуть' in descriptions:
                return True
            if 'Развернуть' in descriptions:
                return False
            parent = parents.get(parent)
    return None

def tab_selected(tree, label):
    if label not in NAV_LABELS:
        return False
    description = 'Маршрутизация' if label == 'Маршруты' else label
    return any(node.get('content-desc') == description and node.get('selected') == 'true'
               for node in tree.iter('node'))