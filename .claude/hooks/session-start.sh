#!/usr/bin/env bash
# Claude Code를 켜면 사용법을 보여주고, 첫 응답에서 상태를 안내하도록 현재 상황을 넘긴다
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

branch=$(git branch --show-current 2>/dev/null)
changes=$(git status --short 2>/dev/null | wc -l | tr -d ' ')
specs=$(ls docs/specs/*.md 2>/dev/null | xargs -r -n1 basename | tr '\n' ' ')

usage='Corte-BE 하네스
- 기능 만들기: "#이슈번호 <기능 설명> 만들어줘" → 명세 한 번 확인받고 끝까지 구현
- 버그 수정: "<증상> 고쳐줘"
- 이어서 하기: "#이슈번호 이어서 해줘"
- 규칙은 AGENTS.md. push와 PR은 요청할 때만 한다.'

context="세션 시작 상태: 브랜치=${branch:-detached}, 변경 파일=${changes}개, 명세=${specs:-없음}.
첫 응답에서 AGENTS.md '진행 방식'대로 이 상태와 할 수 있는 일을 먼저 짧게 안내한다."

json_escape() {
    local s=$1
    s=${s//\\/\\\\}
    s=${s//\"/\\\"}
    s=${s//$'\n'/\\n}
    s=${s//$'\t'/\\t}
    printf '%s' "$s"
}

printf '{"systemMessage":"%s","hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' \
    "$(json_escape "$usage")" "$(json_escape "$context")"
