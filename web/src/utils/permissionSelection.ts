export interface PermissionNode {
  id: number
  parentId?: number | null
}

// 分组只汇总实际子权限；不能因历史数据只保存了父节点就授予所有子项。
export function permissionLeafKeys(nodes: PermissionNode[], selected: number[]): number[] {
  const parents = new Set(nodes.map(node => node.parentId))
  const granted = new Set(selected)
  return nodes.filter(node => !parents.has(node.id) && granted.has(node.id)).map(node => node.id)
}

// 半选分组也随已有子权限保存，但回显时只用叶子节点驱动勾选。
export function normalizePermissionSelection(nodes: PermissionNode[], selected: number[]): number[] {
  const byId = new Map(nodes.map(node => [node.id, node]))
  const result = new Set(permissionLeafKeys(nodes, selected))
  for (const id of [...result]) {
    let parentId = byId.get(id)?.parentId
    const visited = new Set<number>([id])
    while (parentId != null && byId.has(parentId) && !visited.has(parentId)) {
      visited.add(parentId)
      result.add(parentId)
      parentId = byId.get(parentId)?.parentId
    }
  }
  return [...result].sort((a, b) => a - b)
}
