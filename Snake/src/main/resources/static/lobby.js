var Chat = {};
var Players = {};
var InputField;
var socket;
var hid = true;
var name;
var Puntuaciones = {};
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

Players.log = (function (message) {
    var player = document.getElementById('people');
    var p = document.createElement('p');
    p.style.wordWrap = 'break-word';
    p.innerHTML = message.name;
    p.id = message.id;
    player.appendChild(p);
    player.scrollTop = player.scrollHeight;
});


Puntuaciones.log = (function (message) {
    var points = document.getElementById('points');
    var p = document.createElement('p');
    p.style.wordWrap = 'break-word';
    if(tipoPunct === "Arcade"){
        p.innerHTML = message+" s";
    }else{
        p.innerHTML = message+" pts";
    }
    while (points.childNodes.length > 9) {
		points.removeChild(console.lastChild);
	}
    points.appendChild(p);
    points.scrollTop = points.scrollHeight;
});

function CargarPartidas(e) {
    $.ajax({
        method: "GET",
        url: "http://" + window.location.host + "/games/"
    })
        .done(function (msg) {
            let template = $("#template").html();
            $("#games").html("");
            for (var m of msg) {
                let n = m.split(",");
                n[0] = n[0].replace("+", " ");
                let newTemplate = template.replace("%Name", n[0]);
                newTemplate = newTemplate.replace("%Jugadores", n[1] + "/" + n[5]);
                newTemplate = newTemplate.replace("%Id", "'" + n[2] + "'");
                if (n[3] === "Dificil") {
                    newTemplate = newTemplate.replace("%Dificultad", "Hard");
                    newTemplate = newTemplate.replace("white", "lightcoral");
                } else if (n[3] === "Normal") {
                    newTemplate = newTemplate.replace("%Dificultad", "Normal");
                    newTemplate = newTemplate.replace("white", "lightgreen");
                } else if (n[3] === "Facil") {
                    newTemplate = newTemplate.replace("%Dificultad", "Easy");
                    newTemplate = newTemplate.replace("white", "lightblue");
                }
                $("#games").append(newTemplate);
                $("#" + n[2]).click(function (e) {
                    window.alert("te has unido a la partida:" + this.id)
                    window.sessionStorage.setItem("game", this.id);
                    window.location = "http://" + window.location.host + "/game.html";
                })
            }
        });

}


$(function () {
    if(window.sessionStorage.getItem("name") === null){
        window.location = "http://" + window.location.host + "/index.html";
    }

    window.sessionStorage.setItem("Starter", false);
    socket = new WebSocket('ws://' + window.location.host + '/snake');

    socket.onopen = () => socket.send(JSON.stringify({
        id: 0,
        messageType: "connect",
        name: window.sessionStorage.getItem("name"),
        direction: null
    }));
    socket.onmessage = (message) => {
        var packet = JSON.parse(message.data);
        switch (packet.type) {
            case 'join':
                var player = document.getElementById('people');
                player.innerHTML = "";
                for (var j = 0; j < packet.data.length; j++) {
                    Players.log(packet.data[j]);
                }
                break;
            case 'leave':
                $("#" + packet.id).remove();
                break;
            case 'chat':
                Chat.log(packet.data.name + ": " + packet.data.message)
                break;
        }
    }



    $(document).keydown(e => {

        if (e.keyCode === 13) {

            InputField = document.getElementById('inputField');

            if (InputField.value !== "") {
                socket.send(JSON.stringify({
                    id: 0,
                    messageType: "chat",
                    name: window.sessionStorage.getItem("name"),
                    direction: InputField.value
                }));
            }

            InputField.value = "";


        }
    });

    $("#Send").click(function () {
        InputField = document.getElementById('inputField');

        if (InputField.value !== "") {
            socket.send(JSON.stringify({
                id: 0,
                messageType: "chat",
                name: window.sessionStorage.getItem("name"),
                direction: InputField.value
            }));
        }

        InputField.value = "";
    })

    name = window.sessionStorage.getItem("name");
    $("#name").html(name);

    CargarPartidas(null);

    $("#Inputs").hide();
    $("#Crear").click(function (e) {
        if (hid) {
            $("#Inputs").show();
            hid = !hid;
        } else {
            $("#Inputs").hide();
            hid = !hid;
        }
    });
    $("#CreaPartida").click(function (e) {
        let n = {
            name: $("#names").val(),
            dificultad: level,
            Tipo: tipo,
            jugadores: $("#Jugadores").val()
        };
        if (n.name != "") {
            $.ajax({
                method: "POST",
                url: "http://" + window.location.host + "/games/",
                data: JSON.stringify(n),
                contentType: "application/json"
            })
                .done(function (msg) {
                    window.sessionStorage.setItem("Starter", true);
                    window.sessionStorage.setItem("game", msg);
                    window.location = "http://" + window.location.host + "/game.html";

                });
        }

    });

    $("#Recargar").click(CargarPartidas);
    window.setInterval(CargarPartidas, 1000);

    $("#UnirseAletorio").click(function (e) {
        $.ajax({
            method: "GET",
            url: "http://" + window.location.host + "/games/Random",
        })
            .done(function (msg) {
                if (msg >= 1) {
                    window.sessionStorage.setItem("game", msg);
                    window.location = "http://" + window.location.host + "/game.html";
                } else {
                    alert("No hay ninguna partida");
                }
            });
    });

    window.setInterval(function () {
        $.ajax({
            method: "GET",
            url: "http://" + window.location.host + "/games/Puntuaciones/" + tipoPunct,
        })
            .done(function (msg) {
                $("#points").html("");
                for (var m of msg) {
                    Puntuaciones.log(m);
                }
            });



    }, 200);

    $("#Change-Arcade").click(function (e) {
        tipoPunct = "Arcade";
        $("#Hall").html("Hall of Fame(Arcade)");
    });

    $("#Change-Classic").click(function (e) {
        tipoPunct = "Classic";
        $("#Hall").html("Hall of Fame(Classic)");
    });
});
var level = "Facil";
$(document).on('click', '.level', function (e) {

    var thisCheck = $(this);
    level = thisCheck.val();

    if (thisCheck.is(":checked")) {
        thisCheck.parent().parent().parent().find(".level").prop('checked', false);

    }
    thisCheck.prop('checked', true);
});


var tipo = "Arcade";
$(document).on('click', '.Tipe', function (e) {

    var thisCheck = $(this);
    tipo = thisCheck.val();

    if (thisCheck.is(":checked")) {
        thisCheck.parent().parent().parent().find(".Tipe").prop('checked', false);

    }
    thisCheck.prop('checked', true);
});
var tipoPunct = "Arcade";