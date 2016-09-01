hide_troughputs();

function hide_troughputs(){
    var dataCells = document.getElementsByClassName("errorData");
    for (var i = 0; i < dataCells.length; i++) {
            if (dataCells[i].innerText!="" && dataCells[i].innerText!="0"){
                    console.log(dataCells[i].parent);
            }
    }
}

function switch_visibility() {
	for (var i = 0; i < arguments.length; i++) {
		var element = document.getElementById(arguments[i]);
		if (element == null)
			return;

		if (element.classList.contains('collapse')) {
			element.classList.remove('collapse');
			element.classList.add('visible');
		} else {
			element.classList.remove('visible');
			element.classList.add('collapse');
		}
	}
}

function switch_class_by_class(class_name, swap1, swap2) {
	var elements = document.getElementsByClassName(class_name);
	for (var i = 0; i < elements.length; i++) {
		if (elements[i].classList.contains(swap1)) {
			elements[i].classList.remove(swap1);
			elements[i].classList.add(swap2);
		} else if (elements[i].classList.contains(swap2)) {
			elements[i].classList.remove(swap2);
			elements[i].classList.add(swap1);
		}
	}
}

function switch_li_display(id) {
	var element = document.getElementById(id);
	if (element == null)
		return;
	if (element.classList.contains('list-item')) {
		element.classList.remove('list-item');
		element.classList.add('none');
	} else {
		element.classList.remove('none');
		element.classList.add('list-item');
	}
}
function is_checked(id, checked) {
	var element = document.getElementById(id);
	return element == null ? false : element.checked
}
function reset_display(id, checked, display) {
	var element = document.getElementById(id);
	if (element == null)
		return;
	if (checked) {
		element.style.display = display;
	} else {
		element.style.display = 'none';
	}
}