#!/usr/bin/env bash
# Claude Code를 켜면 첫 인사를 보여주고, 첫 응답이 0단계대로 시작하도록 현재 상황을 넘긴다
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

branch=$(git branch --show-current 2>/dev/null)
changes=$(git status --short 2>/dev/null | wc -l | tr -d ' ')

greeting='이 하네스는 원하는 기능을 말로 설명하면 질문으로 빈 부분을 채운 뒤 끝까지 개발해 주는 도구이고,
질문 → GitHub 이슈·브랜치 생성 → 구현·커밋 → push·PR → 쉬운 설명 순서로 진행할게요.

기능을 당신의 말로 설명해주세요!
원하시면 기술 스택도 질문하도록 할게요. 설명 끝에 "기술스택도 질문해줘"라고 이어붙여주세요!'

context="세션 시작 상태: 브랜치=${branch:-detached}, 변경 파일=${changes}개.
첫 응답은 AGENTS.md 4. Feature Workflow 0단계대로 시작한다."

json_escape() {
    local s=$1
    s=${s//\\/\\\\}
    s=${s//\"/\\\"}
    s=${s//$'\n'/\\n}
    s=${s//$'\t'/\\t}
    printf '%s' "$s"
}

printf '{"systemMessage":"%s","hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' \
    "$(json_escape "$greeting")" "$(json_escape "$context")"
