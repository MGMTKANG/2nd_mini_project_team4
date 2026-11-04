FROM gradle:9.7.1-jdk17 AS builder

USER gradle
WORKDIR /home/gradle
# 베이스 이미지의 .gradle 볼륨 밖에 캐시를 두어 Docker 레이어에 보존한다.
ENV GRADLE_USER_HOME=/home/gradle/cache

COPY --chown=gradle:gradle build.gradle settings.gradle ./
# 소스 변경과 별개로 플러그인 및 메인 클래스패스의 실제 의존 파일을 캐시한다.
RUN printf '%s\n' \
    'allprojects {' \
    '    tasks.register("cacheDependencies") {' \
    '        doLast {' \
    '            ["compileClasspath", "runtimeClasspath", "annotationProcessor"].each {' \
    '                configurations.getByName(it).resolve()' \
    '            }' \
    '        }' \
    '    }' \
    '}' > /tmp/cache-dependencies.gradle \
    && gradle --no-daemon --init-script /tmp/cache-dependencies.gradle cacheDependencies \
    && rm /tmp/cache-dependencies.gradle

COPY --chown=gradle:gradle src/main ./src/main
RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:17.0.17_10-jre-noble AS runtime

RUN groupadd --system --gid 10001 sporty \
    && useradd --system --uid 10001 --gid sporty --no-create-home --shell /usr/sbin/nologin sporty
WORKDIR /app
COPY --from=builder --chown=sporty:sporty /home/gradle/build/libs/point-service-*.jar ./app.jar

USER sporty
ENV JAVA_OPTS=""
ENV TZ=Asia/Seoul
EXPOSE 8085
# JAVA_OPTS에는 공백으로 구분한 JVM 옵션을 전달한다. exec로 종료 신호를 JVM에 전달한다.
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
