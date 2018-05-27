var Console = {};
var Chat = {};
var InputField;

Console.log = (function (message) {
	var console = document.getElementById('console');
	var p = document.createElement('p');
	p.style.wordWrap = 'break-word';
	p.innerHTML = message;
	console.appendChild(p);
	while (console.childNodes.length > 25) {
		console.removeChild(console.firstChild);
	}
	console.scrollTop = console.scrollHeight;
});

Chat.log = (function (message) {
	var chat = document.getElementById('chat');
	var p = document.createElement('p');
	p.style.wordWrap = 'break-word';
	p.innerHTML = message;
	chat.appendChild(p);
	while (chat.childNodes.length > 25) {
		chat.removeChild(chat.firstChild);
	}
	chat.scrollTop = chat.scrollHeight;
});

let game;

class Fruit {
	constructor(x, y) {
		this.color = "white";
		this.x = x;
		this.y = y;
	}
	draw(context) {
		context.fillStyle = this.color;
		context.fillRect(this.x, this.y,
			game.gridSize, game.gridSize);
	}
}

class Snake {

	constructor() {
		this.snakeBody = [];
		this.color = null;
	}

	draw(context) {
		for (var pos of this.snakeBody) {
			context.fillStyle = this.color;
			context.fillRect(pos.x, pos.y,
				game.gridSize, game.gridSize);
		}
	}
}

class Game {

	constructor() {

		this.fps = 30;
		this.socket = null;
		this.nextFrame = null;
		this.interval = null;
		this.direction = 'none';
		this.gridSize = 10;

		this.skipTicks = 1000 / this.fps;
		this.nextGameTick = (new Date).getTime();
	}

	initialize() {
		if(window.sessionStorage.getItem("name") === null){
			window.location = "http://" + window.location.host + "/index.html";
		}

		let starter = window.sessionStorage.getItem("Starter");
		if (starter === "false") {
			$("#Comenzar").hide();
		} else {

			$("#Comenzar").click(function (e) {
				game.socket.send(JSON.stringify({
					id: window.sessionStorage.getItem("game"),
					messageType: "Start",
					name: window.sessionStorage.getItem("name"),
					direction: null
				}));
			});
		}
		$("#Cancel").click(function (e) {
			game.socket.send(JSON.stringify({
				id: window.sessionStorage.getItem("game"),
				messageType: "Disconnect",
				name: window.sessionStorage.getItem("name"),
				direction: null
			}));
		})
		$("#Salir").click(function(e){
			window.location = "http://" + window.location.host + "/lobby.html";
		});
		this.fruits = [];
		this.snakes = [];
		let canvas = document.getElementById('playground');
		if (!canvas.getContext) {
			Console.log('Error: 2d canvas not supported by this browser.');
			return;
		}

		this.context = canvas.getContext('2d');
		window.addEventListener('keydown', e => {
			//e.preventDefault();
			var code = e.keyCode;
			if (code > 12 && code < 41) {
				switch (code) {
					case 37:
						if (this.direction != 'east')
							this.setDirection('west');
						e.preventDefault();
						break;
					case 38:
						if (this.direction != 'south')
							this.setDirection('north');
						e.preventDefault();
						break;
					case 39:
						if (this.direction != 'west')
							this.setDirection('east');
						e.preventDefault();
						break;
					case 40:
						if (this.direction != 'north')
							this.setDirection('south');
						e.preventDefault();
						break;
					case 13:
						InputField = document.getElementById('inputField');

						if (InputField.value !== "") {
							game.socket.send(JSON.stringify({
								id: window.sessionStorage.getItem("game"),
								messageType: "chat",
								name: window.sessionStorage.getItem("name"),
								direction: InputField.value
							}));
						}

						InputField.value = "";
				}
			}
		}, false);

		this.connect();
	}

	setDirection(direction) {
		this.direction = direction;
		this.socket.send(JSON.stringify({
			id: window.sessionStorage.getItem("game"),
			messageType: "other",
			name: window.sessionStorage.getItem("name"),
			direction: direction
		}));
		Console.log('Sent: Direction ' + direction);
	}

	startGameLoop() {

		this.socket.send(JSON.stringify({
			id: window.sessionStorage.getItem("game"),
			messageType: "connect",
			name: window.sessionStorage.getItem("name"),
			direction: null
		}));

		this.nextFrame = () => {
			requestAnimationFrame(() => this.run());
		}

		this.nextFrame();
	}

	stopGameLoop() {

		this.nextFrame = null;
		if (this.interval != null) {
			clearInterval(this.interval);
		}
	}

	draw() {
		this.context.clearRect(0, 0, 800, 600);
		for (var id in this.snakes) {
			this.snakes[id].draw(this.context);
		}
		for (var f in this.fruits) {
			this.fruits[f].draw(this.context);
		}
	}

	addSnake(id, color) {
		this.snakes[id] = new Snake();
		this.snakes[id].color = color;
	}

	updateSnake(id, snakeBody) {
		if (this.snakes[id]) {
			this.snakes[id].snakeBody = snakeBody;
		}
	}

	removeSnake(id) {
		this.snakes[id] = null;
		// Force GC.
		delete this.snakes[id];
	}

	run() {

		while ((new Date).getTime() > this.nextGameTick) {
			this.nextGameTick += this.skipTicks;
		}
		this.draw();
		if (this.nextFrame != null) {
			this.nextFrame();
		}
	}

	connect() {

		this.socket = new WebSocket('ws://' + window.location.host + '/snake');

		this.socket.onopen = () => {

			// Socket open.. start the game loop.
			Console.log('Info: WebSocket connection opened.');
			Console.log('Info: Press an arrow key to begin.');
			Chat.log('Conected to chat');

			this.startGameLoop();

			setInterval(() => this.socket.send(JSON.stringify({
				id: window.sessionStorage.getItem("game"),
				messageType: "ping",
				name: window.sessionStorage.getItem("name"),
				direction: null
			})), 5000);
		}

		this.socket.onclose = () => {
			Console.log('Info: WebSocket closed.');
			this.stopGameLoop();
		}

		this.socket.onmessage = (message) => {

			var packet = JSON.parse(message.data);

			switch (packet.type) {
				case 'update':
					$("#Comenzar").hide();
					for (var i = 0; i < packet.data.length; i++) {
						this.updateSnake(packet.data[i].id, packet.data[i].body);
					}
					this.fruits = new Array();
					for (var j = 0; j < packet.fruits.length; j++) {
						this.fruits[j] = new Fruit(packet.fruits[j].x, packet.fruits[j].y);
					}
					$("#Jugadores").html("");
					for (var m of packet.People) {
						$("#Jugadores").append('<h  class ="console" style="background:' + m.color + ';">' + m.nombre + ":" + m.puntos + "</h>");

					}
					var time = packet.Tiempo;
					$("#time").html("<p>" + time + "</p>");
					break;
				case 'join':
					for (var j = 0; j < packet.data.length; j++) {
						this.addSnake(packet.data[j].id, packet.data[j].color);
					}
					for (var j = 0; j < packet.data.length; j++) {
						Console.log(packet.data[j].name + " has joined the game");
					}
					$("#over").hide();
					break;
				case 'leave':
					this.removeSnake(packet.id);
					Console.log("Un jugador ha dejado la partida");
					break;
				case 'dead':
					Console.log('Info: Your snake is dead, bad luck!');
					this.direction = 'none';
					break;
				case 'kill':
					Console.log('Info: Head shot!');
					break;
				case 'chat':
					Chat.log(packet.data.name + ": " + packet.data.message)
					break;
				case 'endGame':
					for (var m of packet.data) {
						if (m.id === window.sessionStorage.getItem("name")) {
							if (m.win) {
								alert("YOU WIN");
							} else {
								alert("YOU LOSE");
							}
							window.location = "http://" + window.location.host + "/lobby.html";
						}

					}
					break;
				case 'failed-join':
					alert("You couldn't join to the game");
					window.location = "http://" + window.location.host + "/lobby.html";
					break;
			}
		}
	}
}

game = new Game();

game.initialize()
