#!/bin/bash
# Initialize git repository for the AI Coder IDE

PROJECT_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "Initializing git repository in: $PROJECT_ROOT"

cd "$PROJECT_ROOT"

# Initialize git if not already done
if [ ! -d ".git" ]; then
    echo "Creating git repository..."
    git init
    
    echo "Configuring git user..."
    git config user.email "aicoder@localhost"
    git config user.name "AI Coder"
    
    echo "Creating initial commit..."
    git add .
    git commit -m "Initial commit - AI Coder IDE"
    
    echo "✓ Git repository initialized successfully!"
else
    echo "✓ Git repository already initialized"
fi

echo ""
echo "Git checkpoint system is ready!"
echo "Checkpoints will be created automatically at the start of each message."
