pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        // Redirect caches into the workspace so we never need to run as root
        NPM_CONFIG_CACHE  = "${WORKSPACE}/.npm"
        PIP_CACHE_DIR     = "${WORKSPACE}/.pip"
        GOCACHE           = "${WORKSPACE}/.gocache"
        GRADLE_USER_HOME  = "${WORKSPACE}/.gradle"
        MAVEN_OPTS        = "-Dmaven.repo.local=${WORKSPACE}/.m2"
    }

    stages {

        // ------------------------------------------------------------------ //
        stage('Checkout') {
        // ------------------------------------------------------------------ //
            steps {
                git branch: 'main',
                    credentialsId: 'github-creds',
                    url: 'https://github.com/AbdurRazzaq004/AbdurRazzaq004.git'
            }
        }

        // ------------------------------------------------------------------ //
        stage('Detect stack') {
        // ------------------------------------------------------------------ //
            steps {
                sh '''
                    echo "====== Repository contents ======"
                    ls -la

                    echo ""
                    echo "====== Stack detection ======"
                    if   [ -f package.json ];        then echo "STACK=node"    > stack.env
                    elif [ -f requirements.txt ] || [ -f setup.py ] || [ -f pyproject.toml ]; then
                                                          echo "STACK=python"  > stack.env
                    elif [ -f pom.xml ];             then echo "STACK=maven"   > stack.env
                    elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then
                                                          echo "STACK=gradle"  > stack.env
                    elif [ -f go.mod ];              then echo "STACK=go"      > stack.env
                    elif [ -f Makefile ];            then echo "STACK=make"    > stack.env
                    else                                  echo "STACK=unknown" > stack.env
                    fi

                    cat stack.env
                '''
            }
        }

        // ------------------------------------------------------------------ //
        stage('Install') {
        // ------------------------------------------------------------------ //
            steps {
                sh '''
                    . ./stack.env

                    echo "Installing dependencies for stack: $STACK"

                    case "$STACK" in
                      node)
                        if [ -f package-lock.json ]; then
                            npm ci
                        else
                            npm install
                        fi
                        ;;
                      python)
                        python3 -m pip install --upgrade pip
                        if   [ -f requirements.txt ]; then
                            pip install -r requirements.txt
                        elif [ -f pyproject.toml ]; then
                            pip install .
                        elif [ -f setup.py ]; then
                            pip install -e .
                        fi
                        pip install pytest pytest-junit 2>/dev/null || true
                        ;;
                      maven)
                        mvn -B dependency:resolve
                        ;;
                      gradle)
                        ./gradlew dependencies --no-daemon || gradle dependencies --no-daemon || true
                        ;;
                      go)
                        go mod download
                        ;;
                      make|unknown)
                        echo "No package manager detected — skipping install."
                        ;;
                    esac
                '''
            }
        }

        // ------------------------------------------------------------------ //
        stage('Lint') {
        // ------------------------------------------------------------------ //
            steps {
                sh '''
                    . ./stack.env

                    echo "Linting for stack: $STACK"

                    case "$STACK" in
                      node)
                        # Honour whatever lint script the project declares
                        if node -e "require('./package.json').scripts.lint" 2>/dev/null; then
                            npm run lint -- --format junit \
                                --output-file reports/eslint-junit.xml 2>/dev/null \
                            || npm run lint || true
                        else
                            echo "No lint script found in package.json — skipping."
                        fi
                        ;;
                      python)
                        pip install flake8 2>/dev/null || true
                        mkdir -p reports
                        flake8 . --max-line-length=120 \
                               --format=junit-xml \
                               --output-file=reports/flake8-junit.xml 2>/dev/null \
                        || flake8 . --max-line-length=120 || true
                        ;;
                      maven)
                        mvn -B checkstyle:check -Dcheckstyle.failOnViolation=false || true
                        ;;
                      gradle)
                        ./gradlew check --no-daemon -x test || true
                        ;;
                      go)
                        which golint && golint ./... || true
                        which staticcheck && staticcheck ./... || true
                        ;;
                      *)
                        echo "No linter configured for stack '$STACK' — skipping."
                        ;;
                    esac
                '''
            }
        }

        // ------------------------------------------------------------------ //
        stage('Unit tests') {
        // ------------------------------------------------------------------ //
            steps {
                sh '''
                    . ./stack.env

                    mkdir -p reports

                    echo "Running tests for stack: $STACK"

                    case "$STACK" in
                      node)
                        if node -e "require('./package.json').scripts.test" 2>/dev/null; then
                            npm test -- --reporters=jest-junit 2>/dev/null \
                            || npm test || true
                        else
                            echo "No test script found in package.json — skipping."
                        fi
                        ;;
                      python)
                        python3 -m pytest \
                            --junitxml=reports/pytest-junit.xml \
                            --tb=short \
                            -v || true
                        ;;
                      maven)
                        mvn -B test \
                            -Dmaven.test.failure.ignore=true
                        ;;
                      gradle)
                        ./gradlew test --no-daemon || true
                        ;;
                      go)
                        go test ./... -v 2>&1 \
                        | tee reports/go-test.log || true
                        # Convert to JUnit if go-junit-report is available
                        if command -v go-junit-report >/dev/null 2>&1; then
                            cat reports/go-test.log \
                            | go-junit-report > reports/go-junit.xml || true
                        fi
                        ;;
                      make)
                        make test || true
                        ;;
                      unknown)
                        echo "No test runner detected — skipping."
                        ;;
                    esac
                '''
            }
        }

        // ------------------------------------------------------------------ //
        stage('Build') {
        // ------------------------------------------------------------------ //
            steps {
                sh '''
                    . ./stack.env

                    echo "Building for stack: $STACK"

                    case "$STACK" in
                      node)
                        if node -e "require('./package.json').scripts.build" 2>/dev/null; then
                            npm run build
                        else
                            echo "No build script in package.json — skipping."
                        fi
                        ;;
                      python)
                        # Build a source distribution / wheel if setup tooling exists
                        if [ -f pyproject.toml ] || [ -f setup.py ]; then
                            pip install build 2>/dev/null || true
                            python3 -m build --outdir dist/ || true
                        else
                            echo "No Python build config found — skipping."
                        fi
                        ;;
                      maven)
                        mvn -B package -DskipTests
                        ;;
                      gradle)
                        ./gradlew assemble --no-daemon
                        ;;
                      go)
                        mkdir -p bin
                        go build -v -o bin/ ./...
                        ;;
                      make)
                        make build || make all || true
                        ;;
                      unknown)
                        echo "No build step configured — skipping."
                        ;;
                    esac
                '''
            }
        }

        // ------------------------------------------------------------------ //
        stage('Archive artifacts') {
        // ------------------------------------------------------------------ //
            steps {
                sh 'echo "Collecting build outputs..."'
                // Archive anything that looks like a distributable output.
                // archiveArtifacts silently skips patterns that match nothing.
                archiveArtifacts allowEmptyArchive: true,
                    artifacts: 'dist/**,build/**,bin/**,target/**/*.jar,target/**/*.war,**/*.whl,**/*.tar.gz',
                    fingerprint: true
            }
        }

    } // end stages

    post {
        always {
            junit allowEmptyResults: true,
                  testResults: '**/reports/*junit*.xml,**/TEST-*.xml,**/test-results/**/*.xml'

            echo "Pipeline finished — status: ${currentBuild.currentResult}"
        }
        success {
            echo "Build PASSED on branch: ${env.BRANCH_NAME ?: 'main'}"
        }
        failure {
            echo "Build FAILED on branch: ${env.BRANCH_NAME ?: 'main'} — check the stage logs above."
        }
        cleanup {
            cleanWs()
        }
    }
}
