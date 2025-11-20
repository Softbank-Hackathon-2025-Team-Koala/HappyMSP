# Database Configuration Guide

## Profile 별 설정

### 1. Local Profile (기본값)
- **Profile**: `local`
- **Database**: H2 메모리 DB

```bash
# 기본 실행 (local profile 자동 활성화)
./gradlew bootRun

# 명시적 지정
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 2. Dev Profile  
- **Profile**: `dev`
- **Database**: MySQL (로컬/개발 서버)

```bash
# MySQL 사용 시
./gradlew bootRun --args='--spring.profiles.active=dev'

# 환경변수로 DB 정보 설정 (선택사항)
export DB_URL="jdbc:mysql://localhost:3306/your_db"
export DB_USERNAME="your_username" 
export DB_PASSWORD="your_password"
```

### 3. Prod Profile
- **Profile**: `prod`  
- **Database**: 프로덕션 DB

```bash
export DB_URL="jdbc:mysql://prod-db:3306/happyMSP"
export DB_USERNAME="prod_user"
export DB_PASSWORD="prod_password"

./gradlew bootRun --args='--spring.profiles.active=prod'
```
