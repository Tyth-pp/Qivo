export interface CategoryNode {
    id: number;
    parentId: number;
    name: string;
    children?: CategoryNode[];
    [key: string]: unknown;
}
/** 接口返回平铺分类；展示树不修改接口原始对象，并保留孤立分类。 */
export function categoryTree(list: CategoryNode[]): CategoryNode[] {
    const map = new Map<number, CategoryNode>();
    function collect(nodes: CategoryNode[]) {
        for (const node of nodes) {
            map.set(node.id, { ...node, children: [] });
            collect(node.children || []);
        }
    }
    collect(list);
    const roots: CategoryNode[] = [];
    for (const node of map.values()) {
        const seen = new Set([node.id]);
        let parent = map.get(node.parentId);
        let cursor = parent;
        while (cursor) {
            if (seen.has(cursor.id)) {
                parent = undefined;
                break;
            }
            seen.add(cursor.id);
            cursor = map.get(cursor.parentId);
        }
        if (parent)
            parent.children!.push(node);
        else
            roots.push(node);
    }
    return roots;
}
