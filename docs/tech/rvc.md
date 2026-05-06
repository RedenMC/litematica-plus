# RVC git based litematica format

RVC 是把litematica接入git的重大尝试。

所有新的类必须写在me.zly2006.rvc包下。

一个RVC schematic是一个git repo，它可以在GitHub上被同步。作为MVP，首先要实现commit。

## 目录结构

- index.json （元数据）
- index.schematic (使用SchematicaSchematic保存的主内容)
- README.md （自动生成）

项目使用jgit操作git。

## git commit 元数据

对于所有使用本模组创建的commit，必须包括自定义git元数据。
使用 jgit 的 ObjectInserter 可以添加元数据。

元数据如下：

- author、committer：name字段使用当前玩家的玩家名字，email字段使用{uuid}@minecraft
- 特殊元数据：rvc-version，取1
- 特殊元数据：x-created-by，取rvc
