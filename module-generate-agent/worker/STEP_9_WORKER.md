# STEP 9 — No AI worker contract

STEP 9 hiện tại là **thuần code (JavaParser + Gradle)**. Không tạo worker
AI để chọn class, chỉnh method hoặc refactor logic. Không sử dụng hợp đồng
worker STEP 9 pilot cũ.

Prime chạy task `generateCaseStudyExplorer` hoặc
`generatePortalCaseStudyExplorer` theo `../STEP_9.md`, tự kiểm tra
fail-closed, Java source projection, symbol/reference indexes, publish và FE.

Worker STEP 8 không có quyền viết STEP 9 output; STEP 9 không có quyền
sửa Java/test/YAML nguồn của STEP 8.
