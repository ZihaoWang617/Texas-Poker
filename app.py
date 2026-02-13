from flask import Flask, render_template, jsonify
import os

app = Flask(__name__, 
            static_folder='src/main/resources/static',
            template_folder='src/main/resources/static')

@app.route('/')
def index():
    return open('src/main/resources/static/index.html', encoding='utf-8').read()

@app.route('/api/game/health')
def health():
    return jsonify({
        "code": 200,
        "message": "WePoker Game Server is running",
        "data": {
            "status": "healthy",
            "version": "1.0.0"
        }
    })

@app.route('/api/game/tables')
def tables():
    return jsonify({
        "code": 200,
        "message": "Success",
        "data": []
    })

if __name__ == '__main__':
    print("🚀 WePoker Frontend Server starting...")
    print("📍 Access the game at: http://localhost:8080")
    app.run(host='0.0.0.0', port=8080, debug=False)
