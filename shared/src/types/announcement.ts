/**
 * 与后端 announcement/entity/AnnouncementLevel 一致。
 * 声明顺序即优先级顺序，`URGENT` 最高（待读查询按此取一条）。
 *
 * 双端共用：学生端用于强制阅读弹窗，管理端用于公告列表与新建表单（`types/admin.ts`
 * 此前是 `export type { AnnouncementLevel }` 复用学生端这一份，拆包后改为引用本文件，
 * 仍保持「枚举只有一处定义」）。
 */
export type AnnouncementLevel = 'URGENT' | 'NORMAL' | 'ACTIVITY'
